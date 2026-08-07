package ch.ubique.backend.job.service.executor.heartbeat;

import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.service.JobService;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.management.ThreadMXBean;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Owns the single, shared, small scheduler pool every {@link RunHeartbeat} ticks on - not a
 * dedicated thread per running job - and tracks each currently-active one by {@code jobRunId}, so
 * anything that observes a run is no longer actually going (finished, marked {@code UNFINISHED},
 * cancelled, ...) can stop its heartbeat immediately via {@link #stop} instead of waiting for that
 * heartbeat's own next tick to notice on its own.
 *
 * <p>Not profile-restricted: needed both on primary instances and inside dedicated {@code --job}
 * pods (see {@code JobRunnerHelper}).
 */
@Component
public class HeartbeatRegistry {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatRegistry.class);

    private static final int POOL_SIZE = 4;

    private final ScheduledExecutorService scheduler;
    private final Map<Integer, RunHeartbeat> active = new ConcurrentHashMap<>();

    public HeartbeatRegistry() {
        this.scheduler =
                Executors.newScheduledThreadPool(
                        POOL_SIZE,
                        r -> {
                            final var thread = new Thread(r, "job-heartbeat");
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    public RunHeartbeat start(final JobService jobService, final JobRunContext jobRunContext) {
        final var jobRunId = jobRunContext.jobRun().id();
        final var heartbeat = new RunHeartbeat(jobService, jobRunContext, scheduler, this);
        active.put(jobRunId, heartbeat);
        return heartbeat;
    }

    /**
     * Called by a {@link RunHeartbeat} only once it has stopped itself, so it's no longer tracked.
     */
    void forget(final int jobRunId) {
        active.remove(jobRunId);
    }

    /**
     * Stops and forgets whatever heartbeat this instance is currently tracking for the given job
     * run, if any - a no-op if nothing is tracked for it here (e.g. it's actually running on a
     * different instance, or already finished on its own). Safe to call from anywhere that observes
     * a run isn't active anymore: normal completion, external abandonment, cancellation, ...
     */
    public void stop(final int jobRunId) {
        final var heartbeat = active.remove(jobRunId);
        if (heartbeat != null) {
            heartbeat.forceStop();
        }
    }

    @PreDestroy
    void shutdown() {
        logger.debug("Shutting down shared heartbeat scheduler ({} still tracked).", active.size());
        scheduler.shutdownNow();
    }

    /** Schedule a task similar to a {@link RunHeartbeat} on the same thread pool. */
    public ScheduledFuture<?> scheduleWithFixedDelay(
            final Runnable command,
            final long initialDelay,
            final long delay,
            final TimeUnit timeUnit) {
        return scheduler.scheduleWithFixedDelay(command, initialDelay, delay, timeUnit);
    }
}
