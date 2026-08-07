package ch.uetlib.job.service.executor.heartbeat;

import ch.uetlib.job.model.JobRunContext;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.service.JobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps a run's {@code t_job_run_queue} row heartbeat honest for as long as the run itself is
 * going, switching how it verifies that as the run moves between phases:
 *
 * <ul>
 *   <li>while {@code job.run(...)} is executing synchronously on this JVM's worker thread, {@link
 *       ThreadProgressCheckIn} checks that thread's own progress
 *   <li>once {@code job.run(...)} returns {@link JobRunState#SCHEDULED} - meaning it handed
 *       execution off to something outside this thread (a Kubernetes pod, an external service) and
 *       returned - this thread's own progress is meaningless, so {@link ScheduledElsewhereCheckIn}
 *       instead just watches the run's own state
 *   <li>as soon as the run reaches any state other than {@link JobRunState#SCHEDULED} - {@code
 *       job.run(...)} throwing, returning a terminal state, or {@link ScheduledElsewhereCheckIn}
 *       observing the run moved on - check-ins are force-stopped immediately, so an already-queued
 *       tick never fires again for a run that's actually finished
 * </ul>
 *
 * <p>{@link ThreadProgressCheckIn} only ever watches the single worker thread {@code job.run(...)}
 * itself executes on, not any child threads a job might spawn (e.g. virtual threads for its own
 * fan-out work). A job whose main thread sits idle waiting on such children - rather than doing its
 * own CPU work - should call {@link ch.uetlib.job.service.JobService#touchRunningHeartbeat}
 * itself during that wait; see its javadoc.
 *
 * @see ch.uetlib.job.service.executor.JobQueueWatcherService#reconcile
 * @see HeartbeatRegistry
 */
public final class RunHeartbeat {

    private static final Logger logger = LoggerFactory.getLogger(RunHeartbeat.class);

    /**
     * How often the runner actually executing a job checks in on its queue row's heartbeat. Should
     * be much lower than {@link
     * ch.uetlib.job.model.properties.JobPropertyDefinitions#HEARTBEAT_TIMEOUT}'s default in
     * order that a couple of skipped beats don't immediately look like an abandoned run.
     */
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private final JobService jobService;
    private final JobRunContext jobRunContext;
    private final int jobRunId;
    private final HeartbeatRegistry registry;
    private final ScheduledFuture<?> future;
    private final AtomicReference<CheckIn> checkIn;

    RunHeartbeat(
            final JobService jobService,
            final JobRunContext jobRunContext,
            final ScheduledExecutorService scheduler,
            final HeartbeatRegistry registry) {
        this.jobService = jobService;
        this.jobRunContext = jobRunContext;
        this.jobRunId = jobRunContext.jobRun().id();
        this.registry = registry;
        this.checkIn =
                new AtomicReference<>(
                        new ThreadProgressCheckIn(
                                jobService,
                                jobRunContext,
                                CheckInState.start(Thread.currentThread())));
        this.future =
                scheduler.scheduleWithFixedDelay(
                        this::tick,
                        HEARTBEAT_INTERVAL.toSeconds(),
                        HEARTBEAT_INTERVAL.toSeconds(),
                        TimeUnit.SECONDS);
    }

    JobRunContext jobRunContext() {
        return jobRunContext;
    }

    /** Called once {@code job.run(...)} (or its exception handling) has produced a final state. */
    public void onLocalExecutionFinished(final JobRunState state) {
        if (state == JobRunState.SCHEDULED) {
            checkIn.set(new ScheduledElsewhereCheckIn(jobService, jobRunId));
            return;
        }
        forceStop();
    }

    /**
     * Stops this heartbeat immediately and forgets it in {@link HeartbeatRegistry} - safe to call
     * from anywhere (this run's own natural completion, or an external observer like {@code
     * JobQueueWatcherService} that decided this run isn't active anymore), and safe to call more
     * than once.
     */
    void forceStop() {
        // cancel(true), not false: even a check-in already queued for the next tick must not run
        // again once the job run is finished.
        future.cancel(true);
        registry.forget(jobRunId);
    }

    /** Package-visible for tests, which can't reasonably wait on the real schedule. */
    void tick() {
        if (!checkIn.get().checkIn(this::touchHeartbeat)) {
            future.cancel(false);
            registry.forget(jobRunId);
        }
    }

    CheckIn currentCheckIn() {
        return checkIn.get();
    }

    boolean isStopped() {
        return future.isCancelled();
    }

    private void touchHeartbeat(final Instant at) {
        try {
            jobService.touchRunningHeartbeat(jobRunId);
        } catch (final Exception e) {
            logger.warn("Failed to touch heartbeat for job run {}.", jobRunId, e);
        }
    }
}
