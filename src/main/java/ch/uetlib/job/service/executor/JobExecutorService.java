package ch.uetlib.job.service.executor;

import static ch.uetlib.job.model.properties.enums.JobRunnerKey.CURRENT;
import static ch.uetlib.job.model.properties.enums.JobRunnerKey.DEDICATED_K8S_JOB;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import ch.uetlib.core.utils.MapCollectors;
import ch.uetlib.job.Job;
import ch.uetlib.job.model.*;
import ch.uetlib.job.model.executer.JobSubmissionResult;
import ch.uetlib.job.model.executer.JobSubmissionStatus;
import ch.uetlib.job.model.executer.SuccessfulSubmissionResult;
import ch.uetlib.job.model.executer.UnsuccessfulSubmissionResult;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.properties.JobPropertyDefinitions;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.executor.runners.JobRunner;

import org.apache.commons.lang3.NotImplementedException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class JobExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(JobExecutorService.class);

    private final JobService jobService;

    private final Map<JobRunnerKey, JobRunner> jobRunners;

    public JobExecutorService(final JobService jobService, final Collection<JobRunner> jobRunners) {
        this.jobService = jobService;
        this.jobRunners = jobRunners.stream().collect(MapCollectors.toMap(JobRunner::key));
    }

    public JobSubmissionResult submit(final Class<? extends Job> jobClass) {
        return submit(jobClass, null);
    }

    public JobSubmissionResult submit(final JobQueuedRun jobQueuedRun, final boolean isDedicated) {
        if (jobQueuedRun.jobRunId() != null) {
            // TODO: Implement rescheduling jobs if required
            throw new NotImplementedException("Behavior of rescheduling jobs is TBD");
        }
        final var jobClass = jobService.findJobById(jobQueuedRun.jobId()).orElseThrow();
        return submit(
                jobService.getJobClassOrThrow(jobClass),
                jobQueuedRun.arguments(),
                null,
                isDedicated ? DEDICATED_K8S_JOB : CURRENT);
    }

    public JobSubmissionResult submit(
            final Class<? extends Job> jobClass, @Nullable final JobRunArguments arguments) {
        return submit(jobClass, arguments, null, null);
    }

    public JobSubmissionResult submit(
            final Class<? extends Job> jobClass,
            @Nullable final JobRunArguments arguments,
            @Nullable final Integer runId,
            @Nullable final JobRunnerKey jobRunnerKey) {

        if (!jobService.isEnabled(jobClass.getCanonicalName())) {
            if (jobService.exists(jobClass.getCanonicalName())) {
                logger.info("Job {} is disabled and will not be submitted.", jobClass);
            } else {
                logger.info("Job class {} does not exist.", jobClass);
            }
            return new UnsuccessfulSubmissionResult(JobSubmissionStatus.DISALLOWED);
        }

        final var job = jobService.instantiateJob(jobClass);
        final var isForceRun =
                Optional.ofNullable(arguments).map(JobRunArguments::forceRun).orElse(false);
        final var allowParallelScheduling =
                job.allowParallelRuns() || (isForceRun && job.forceRunOverridesAllowParallelRuns());
        if (!allowParallelScheduling && isCurrentlyRunning(job) && !isSameAsLastRun(job, runId)) {
            logger.info("Job is already running and parallel runs are disallowed.");
            return new UnsuccessfulSubmissionResult(JobSubmissionStatus.DISALLOWED);
        }

        logger.info("{} submitted to executor.", jobClass);
        final var selectedJobRunnerKey = getSelectedJobRunnerKey(job, jobRunnerKey);
        final var jobRunContext = createContext(job, runId, selectedJobRunnerKey, arguments);
        final var runner = Objects.requireNonNull(jobRunners.get(selectedJobRunnerKey));

        final var jobRun = jobRunContext.jobRun();
        final var previousContextMap = MDC.getCopyOfContextMap();
        MDC.put("jobRunId", String.valueOf(jobRun.id()));

        final var scheduled = tryToSchedule(runner, job, jobRunContext);

        if (!scheduled) {
            jobService.updateJobRunState(jobRun, JobRunState.ERROR, Instant.now());
            logger.error("Job {} could not be scheduled", job);
            MDC.setContextMap(previousContextMap);
            return new UnsuccessfulSubmissionResult(JobSubmissionStatus.FAILED);
        }

        logger.info("Job {} successfully scheduled", job.getClass().getCanonicalName());
        MDC.setContextMap(previousContextMap);
        return new SuccessfulSubmissionResult(runner.key(), jobRunContext, jobRun.id());
    }

    protected boolean tryToSchedule(
            final JobRunner runner, final Job job, final JobRunContext jobRunContext) {
        try {
            logger.info(
                    "Scheduling job {} with {} using {}",
                    job.getClass().getCanonicalName(),
                    jobRunContext,
                    runner.getClass().getSimpleName());
            return runner.submit(job, jobRunContext);
        } catch (final Exception e) {
            logger.warn("Unhandled exception while trying to schedule job.", e);
            return false;
        }
    }

    private boolean isCurrentlyRunning(final Job job) {
        return jobService
                .findJobId(job.getClass())
                .flatMap(jobService::getLastJobRun)
                .map(JobRun::latestState)
                .map(JobRunState::isRunning)
                .orElse(false);
    }

    private boolean isSameAsLastRun(final Job job, final @Nullable Integer runId) {
        if (runId == null) {
            return false;
        }
        final var lastRun = jobService.findJobId(job.getClass()).flatMap(jobService::getLastJobRun);
        return lastRun.isPresent() && lastRun.get().id() == runId;
    }

    private JobRunContext createContext(
            final Job job,
            @Nullable final Integer runId,
            final JobRunnerKey runnerKey,
            @Nullable final JobRunArguments nullableArguments) {
        final var arguments =
                requireNonNull(requireNonNullElse(nullableArguments, JobRunArguments.DEFAULT));
        final var jobClassAndId = jobService.jobClassAndIdOrThrow(job.getClass());
        final var jobRun = jobService.getOrCreateJobRun(job, runId, runnerKey, arguments);
        final var jobProperties = jobService.getProperties(job);
        return new JobRunContext(jobClassAndId, jobRun, arguments, jobProperties);
    }

    private JobRunnerKey getSelectedJobRunnerKey(
            final Job job, @Nullable final JobRunnerKey jobRunnerKey) {
        final var jobProperties = jobService.getProperties(job);

        final var selectedJobRunnerKey =
                Objects.requireNonNullElseGet(
                        jobRunnerKey, () -> getRunnerKeyFromJobProperties(jobProperties));

        if (!jobRunners.containsKey(selectedJobRunnerKey)) {
            throw new NoSuchElementException(
                    "Could not find a runner for the given runner key " + selectedJobRunnerKey);
        }

        return requireNonNull(selectedJobRunnerKey);
    }

    private JobRunnerKey getRunnerKeyFromJobProperties(final JobProperties jobProperties) {
        return requireNonNull(
                requireNonNullElse(
                        jobProperties.get(JobPropertyDefinitions.RUNNER).value(),
                        JobPropertyDefinitions.RUNNER.defaultValue().orElseThrow()));
    }
}
