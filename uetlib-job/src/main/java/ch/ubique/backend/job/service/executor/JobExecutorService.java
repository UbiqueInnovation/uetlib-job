package ch.ubique.backend.job.service.executor;

import ch.ubique.backend.core.utils.MapCollectors;
import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.*;
import ch.ubique.backend.job.model.properties.JobProperties;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.executor.runners.JobRunner;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

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

    public void submit(@NotNull final Class<? extends Job> jobClass) {
        submit(jobClass, null);
    }

    public void submit(
            @NotNull final Class<? extends Job> jobClass,
            @Nullable final JobRunArguments arguments) {
        submit(jobClass, arguments, null, null);
    }

    public void submit(
            @NotNull final Class<? extends Job> jobClass,
            @Nullable final JobRunArguments arguments,
            @Nullable final Integer runId,
            @Nullable final JobRunnerKey jobRunnerKey) {

        final var job = jobService.instantiateJob(jobClass);
        final var isForceRun =
                Optional.ofNullable(arguments).map(JobRunArguments::forceRun).orElse(false);
        final var allowParallelScheduling =
                job.allowParallelRuns() || (isForceRun && job.forceRunOverridesAllowParallelRuns());
        if (!allowParallelScheduling && isCurrentlyRunning(job) && !isSameAsLastRun(job, runId)) {
            logger.info("Job is already running and parallel runs are disallowed.");
            return;
        }

        logger.info("{} submitted to executor.", jobClass);
        final var selectedJobRunnerKey = getSelectedJobRunnerKey(job, jobRunnerKey);
        final var jobRunContext = createContext(job, runId, selectedJobRunnerKey, arguments);
        final var runner = jobRunners.get(selectedJobRunnerKey);

        final var jobRun = jobRunContext.jobRun();
        final var previousContextMap = MDC.getCopyOfContextMap();
        MDC.put("jobRunId", String.valueOf(jobRun.id()));

        final var scheduled = tryToSchedule(runner, job, jobRunContext);

        if (scheduled) {
            logger.info("Job {} successfully scheduled", job);
        } else {
            jobService.updateJobRunState(
                    jobRun,
                    JobRunState.ERROR,
                    Instant.now(),
                    getClass().getCanonicalName() + "#submit -> Could not be scheduled");
            logger.error("Job {} could not be scheduled", job);
        }

        MDC.setContextMap(previousContextMap);
    }

    private boolean tryToSchedule(
            final JobRunner runner, final Job job, final JobRunContext jobRunContext) {
        try {
            logger.info(
                    "Scheduling job {} with jobRunContext {} using runner {}",
                    job,
                    jobRunContext,
                    runner.getClass());
            return runner.submit(job, jobRunContext);
        } catch (final Exception e) {
            logger.warn("Unhandled exception while trying to schedule job.", e);
            return false;
        }
    }

    private boolean isCurrentlyRunning(final Job job) {
        return jobService
                .getLastJobRun(jobService.getId(job))
                .map(JobRun::latestState)
                .map(JobRunState::isRunning)
                .orElse(false);
    }

    private boolean isSameAsLastRun(final Job job, final Integer runId) {
        if (runId == null) {
            return false;
        }
        final var lastRun = jobService.getLastJobRun(jobService.getId(job));
        return lastRun.isPresent() && lastRun.get().id() == runId;
    }

    @NotNull
    private JobRunContext createContext(
            @NotNull final Job job,
            @Nullable final Integer runId,
            @NotNull final JobRunnerKey runnerKey,
            @Nullable final JobRunArguments nullableArguments) {
        final var arguments =
                Objects.requireNonNullElse(nullableArguments, JobRunArguments.DEFAULT);
        final var jobClassAndId = jobService.getJobClassAndId(job);
        final var jobRun = jobService.getOrCreateJobRun(job, runId, runnerKey, arguments);
        final var jobProperties = jobService.getProperties(job);
        return new JobRunContext(jobClassAndId, jobRun, arguments, jobProperties);
    }

    @NotNull
    private JobRunnerKey getSelectedJobRunnerKey(
            @NotNull final Job job, @Nullable final JobRunnerKey jobRunnerKey) {
        final var jobProperties = jobService.getProperties(job);

        final var selectedJobRunnerKey =
                Objects.requireNonNullElseGet(
                        jobRunnerKey, () -> getRunnerKeyFromJobProperties(jobProperties));

        if (!jobRunners.containsKey(selectedJobRunnerKey)) {
            throw new NoSuchElementException(
                    "Could not find a runner for the given runner key " + selectedJobRunnerKey);
        }

        return selectedJobRunnerKey;
    }

    @NotNull
    private JobRunnerKey getRunnerKeyFromJobProperties(@NotNull final JobProperties jobProperties) {
        return jobProperties.get(JobPropertyDefinitions.RUNNER).value();
    }
}
