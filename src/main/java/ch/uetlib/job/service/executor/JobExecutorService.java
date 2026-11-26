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
import ch.uetlib.job.service.instance.JobInstanceIdentity;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

@Service
public class JobExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(JobExecutorService.class);

    private final JobService jobService;
    private final JobInstanceIdentity instanceIdentity;

    private final Map<JobRunnerKey, JobRunner> jobRunners;

    public JobExecutorService(
            final JobService jobService,
            final JobInstanceIdentity instanceIdentity,
            final Collection<JobRunner> jobRunners) {
        this.jobService = jobService;
        this.instanceIdentity = instanceIdentity;
        this.jobRunners = jobRunners.stream().collect(MapCollectors.toMap(JobRunner::key));
    }

    /**
     * Executes a claimed row; a set {@code jobRunId} marks a {@code BaseStateJob} handoff, whose
     * run is reused.
     */
    public JobSubmissionResult submit(final JobQueuedRun jobQueuedRun, final boolean isDedicated) {
        final var jobClass = jobService.findJobById(jobQueuedRun.jobId()).orElseThrow();
        return submit(
                jobService.getJobClassOrThrow(jobClass),
                jobQueuedRun.arguments(),
                jobQueuedRun.jobRunId(),
                isDedicated ? DEDICATED_K8S_JOB : CURRENT,
                job -> parallelRunRefusal(job, jobQueuedRun),
                context -> linkClaimedRow(jobQueuedRun, context.jobRun()));
    }

    /**
     * Whether {@code jobQueuedRun} may start right now: its job is enabled, and it may run in
     * parallel or no other run of the job is under way. The dispatcher claims only such a row;
     * {@link #submit} checks again, since another instance may start a run in between.
     */
    public boolean canStart(final JobQueuedRun jobQueuedRun) {
        final var jobClass =
                jobService.getJobClassOrThrow(
                        jobService.findJobById(jobQueuedRun.jobId()).orElseThrow());
        return disabledRefusal(jobClass).isEmpty()
                && parallelRunRefusal(jobService.instantiateJob(jobClass), jobQueuedRun).isEmpty();
    }

    /** Entry point of a dedicated {@code --job} process; records the run's RUNNING row itself. */
    public JobSubmissionResult submitDedicatedJobRun(
            final Class<? extends Job> jobClass,
            final JobRunArguments arguments,
            @Nullable final Integer runId) {
        return submit(
                jobClass,
                arguments,
                runId,
                DEDICATED_K8S_JOB,
                job ->
                        runId != null
                                ? Optional.empty()
                                : parallelRunRefusal(job, arguments, null, false),
                context ->
                        jobService.queueRunningRun(
                                context.job(),
                                context.arguments(),
                                context.jobRun().id(),
                                instanceIdentity.name()));
    }

    /**
     * @param parallelRunRefusal why the run may not start next to one under way; empty if it may.
     * @param recordInQueue runs before the runner starts, as a {@code BaseStateJob} handoff may
     *     re-queue the row at once. A run it can't record is not started.
     */
    private JobSubmissionResult submit(
            final Class<? extends Job> jobClass,
            @Nullable final JobRunArguments arguments,
            @Nullable final Integer runId,
            @Nullable final JobRunnerKey jobRunnerKey,
            final Function<Job, Optional<String>> parallelRunRefusal,
            final Consumer<JobRunContext> recordInQueue) {

        final var disabled = disabledRefusal(jobClass);
        if (disabled.isPresent()) {
            return disallowed(disabled.get());
        }

        final var job = jobService.instantiateJob(jobClass);
        final var parallelRun = parallelRunRefusal.apply(job);
        if (parallelRun.isPresent()) {
            return disallowed(parallelRun.get());
        }

        logger.info("{} submitted to executor.", jobClass);
        final var selectedJobRunnerKey = getSelectedJobRunnerKey(job, jobRunnerKey);
        final var jobRunContext = createContext(job, runId, selectedJobRunnerKey, arguments);
        final var runner = Objects.requireNonNull(jobRunners.get(selectedJobRunnerKey));

        final var jobRun = jobRunContext.jobRun();
        final var previousContextMap = MDC.getCopyOfContextMap();
        MDC.put("jobRunId", String.valueOf(jobRun.id()));

        if (!tryToRecordInQueue(recordInQueue, jobRunContext)
                || !tryToSchedule(runner, job, jobRunContext)) {
            jobService.updateJobRunState(jobRun, JobRunState.ERROR, Instant.now());
            logger.error("Job {} could not be scheduled", job);
            MDC.setContextMap(previousContextMap);
            return new UnsuccessfulSubmissionResult(JobSubmissionStatus.FAILED);
        }

        logger.info("Job {} successfully scheduled", job.getClass().getCanonicalName());
        MDC.setContextMap(previousContextMap);
        return new SuccessfulSubmissionResult(runner.key(), jobRunContext, jobRun.id());
    }

    private void linkClaimedRow(final JobQueuedRun queuedRun, final JobRun jobRun) {
        if (!jobService.setRunId(queuedRun, jobRun.id())) {
            logger.warn(
                    "Queue row {} was no longer claimed when linking run {}; leaving it to"
                            + " reconciliation.",
                    queuedRun.jobQueuedRunId(),
                    jobRun.id());
        }
    }

    private boolean tryToRecordInQueue(
            final Consumer<JobRunContext> recordInQueue, final JobRunContext jobRunContext) {
        try {
            recordInQueue.accept(jobRunContext);
            return true;
        } catch (final Exception e) {
            logger.warn(
                    "Could not record job run {} in the queue; not starting it.",
                    jobRunContext.jobRun().id(),
                    e);
            return false;
        }
    }

    private boolean tryToSchedule(
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

    private static JobSubmissionResult disallowed(final String reason) {
        logger.info("{}; not submitting it.", reason);
        return new UnsuccessfulSubmissionResult(JobSubmissionStatus.DISALLOWED);
    }

    /** Why no run of {@code jobClass} may start at all; empty if one may. */
    private Optional<String> disabledRefusal(final Class<? extends Job> jobClass) {
        if (jobService.isEnabled(jobClass.getCanonicalName())) {
            return Optional.empty();
        }
        return Optional.of(
                jobService.exists(jobClass.getCanonicalName())
                        ? "Job %s is disabled".formatted(jobClass)
                        : "Job class %s does not exist".formatted(jobClass));
    }

    private Optional<String> parallelRunRefusal(final Job job, final JobQueuedRun jobQueuedRun) {
        return parallelRunRefusal(
                job, jobQueuedRun.arguments(), jobQueuedRun.jobRunId(), jobQueuedRun.runNow());
    }

    private Optional<String> parallelRunRefusal(
            final Job job,
            @Nullable final JobRunArguments arguments,
            @Nullable final Integer runId,
            final boolean runNow) {
        if (mayRunInParallel(job, arguments, runId, runNow)) {
            return Optional.empty();
        }
        final var blocked =
                runId == null
                        ? hasRunUnderWay(job)
                        : isCurrentlyRunning(job) && !isSameAsLastRun(job, runId);
        if (blocked) {
            return Optional.of(
                    "Job %s is already running and parallel runs are disallowed"
                            .formatted(job.getClass()));
        }
        return Optional.empty();
    }

    /** Note: Run-now applies to fresh runs only, never to a {@code BaseStateJob} continuation. */
    private boolean mayRunInParallel(
            final Job job,
            @Nullable final JobRunArguments arguments,
            @Nullable final Integer runId,
            final boolean runNow) {
        if (runNow && runId == null) {
            return true;
        }
        final var jobProperties = jobService.getProperties(job);
        final var isForceRun =
                Optional.ofNullable(arguments).map(JobRunArguments::forceRun).orElse(false);
        return Boolean.TRUE.equals(
                        jobProperties.getValue(JobPropertyDefinitions.ALLOW_PARALLEL_RUNS))
                || (isForceRun
                        && Boolean.TRUE.equals(
                                jobProperties.getValue(
                                        JobPropertyDefinitions
                                                .FORCE_RUN_OVERRIDES_ALLOW_PARALLEL_RUNS)));
    }

    private boolean hasRunUnderWay(final Job job) {
        return jobService.findJobId(job.getClass()).map(jobService::hasRunningJobRun).orElse(false);
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
