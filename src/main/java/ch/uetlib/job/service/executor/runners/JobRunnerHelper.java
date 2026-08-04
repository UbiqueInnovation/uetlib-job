package ch.uetlib.job.service.executor.runners;

import ch.uetlib.job.Job;
import ch.uetlib.job.model.JobRunContext;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.service.JobService;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class JobRunnerHelper {

    private static final Logger logger = LoggerFactory.getLogger(JobRunnerHelper.class);

    /**
     * How often the runner actually executing a job proves it is still alive by touching its queue
     * row's heartbeat. Comfortably below {@link
     * ch.uetlib.job.model.properties.JobPropertyDefinitions#HEARTBEAT_TIMEOUT}'s default so
     * a couple of missed beats don't immediately look like an abandoned run.
     */
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private JobRunnerHelper() {}

    static void run(
            final Job job, final JobService jobService, final JobRunContext initialContext) {
        final var jobRunContext = prepareRun(jobService, initialContext);
        final var previousContextMap = MDC.getCopyOfContextMap();
        MDC.put("jobRunId", String.valueOf(jobRunContext.jobRun().id()));
        runJob(job, jobRunContext, jobService);
        MDC.setContextMap(previousContextMap);
    }

    static JobRunContext prepareRun(
            final JobService jobService, final JobRunContext initialContext) {
        final var startedJobRun = jobService.startJobRun(initialContext.jobRun(), Instant.now());
        return new JobRunContext(
                initialContext.job(),
                startedJobRun,
                initialContext.arguments(),
                initialContext.properties());
    }

    static void runJob(
            final Job job, final JobRunContext jobRunContext, final JobService jobService) {
        final var heartbeat = startHeartbeat(jobService, jobRunContext.jobRun().id());
        try {
            logger.info(
                    "Running {} on {} with {}",
                    job.getClass().getCanonicalName(),
                    Thread.currentThread().getName(),
                    jobRunContext);
            final var state = job.run(jobRunContext);
            logger.info("Job {} finished with state {}", job.getClass().getCanonicalName(), state);
            updateJobRunState(jobService, jobRunContext, state);
        } catch (final Exception e) {
            final var rootCause = Objects.requireNonNullElse(ExceptionUtils.getRootCause(e), e);
            if (rootCause instanceof InterruptedException) {
                logger.info("Job {} interrupted", job);
                updateJobRunState(jobService, jobRunContext, JobRunState.CANCELLED);
                Thread.currentThread().interrupt();
            } else {
                logger.error("Exception running job: ", e);
                updateJobRunState(jobService, jobRunContext, JobRunState.ERROR);
            }
        } finally {
            heartbeat.shutdownNow();
        }
    }

    /**
     * Proves this run is actually alive for as long as {@code job.run(...)} is executing, regardless
     * of what {@code t_job_run}'s own state says - see {@link
     * ch.uetlib.job.service.executor.JobQueueWatcherService#reconcile}, which treats a
     * RUNNING queue row whose heartbeat goes stale as abandoned.
     */
    private static ScheduledExecutorService startHeartbeat(
            final JobService jobService, final int jobRunId) {
        final var executor =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            final var thread = new Thread(r, "job-run-heartbeat-" + jobRunId);
                            thread.setDaemon(true);
                            return thread;
                        });
        executor.scheduleWithFixedDelay(
                () -> {
                    try {
                        jobService.touchRunningHeartbeat(jobRunId);
                    } catch (final Exception e) {
                        logger.warn("Failed to touch heartbeat for job run {}.", jobRunId, e);
                    }
                },
                HEARTBEAT_INTERVAL.toSeconds(),
                HEARTBEAT_INTERVAL.toSeconds(),
                TimeUnit.SECONDS);
        return executor;
    }

    /**
     * In most cases, this is will finish the job and set the finishedAt timestamp. But, in the case
     * of a StateJob, this new state might {@link JobRunState#SCHEDULED} if the actual job is
     * scheduled as a new pod in the k8s.
     */
    static void updateJobRunState(
            final JobService jobService,
            final JobRunContext jobRunContext,
            final JobRunState state) {
        final var transitionedAt = Instant.now();
        jobService.updateJobRunState(jobRunContext.jobRun(), state, transitionedAt);
    }
}
