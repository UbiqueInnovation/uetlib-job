package ch.uetlib.job.service.executor.heartbeat;

import ch.uetlib.job.model.JobRunContext;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.service.JobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Verifies the actual JVM worker thread running {@code job.run(...)} is both alive and making CPU
 * progress. Touching the heartbeat on a timer alone would only prove the heartbeat's own background
 * thread is still ticking, not that the worker is doing anything - a worker stuck forever on a
 * lock, a condition, or an unbounded blocking call would look perfectly healthy otherwise.
 *
 * <p>CPU progress can only be measured for the one platform thread {@code job.run(...)} itself
 * executes on ({@link CheckInState} falls back to {@code isAlive()}-only if that thread turns out
 * to be virtual, since per-thread CPU time isn't supported for virtual threads). It has no
 * visibility into any child threads a job spawns for its own fan-out work, so a main thread that's
 * legitimately just waiting on those children - not doing CPU work itself - looks identical to a
 * stuck one here. See {@link ch.uetlib.job.service.JobService#touchRunningHeartbeat} for
 * how such a job should prove its own progress.
 */
final class ThreadProgressCheckIn implements CheckIn {

    private static final Logger logger = LoggerFactory.getLogger(ThreadProgressCheckIn.class);

    private final JobService jobService;
    private final JobRunContext jobRunContext;
    private CheckInState state;

    ThreadProgressCheckIn(
            final JobService jobService,
            final JobRunContext jobRunContext,
            final CheckInState initial) {
        this.jobService = jobService;
        this.jobRunContext = jobRunContext;
        this.state = initial;
    }

    @Override
    public boolean checkIn(final Consumer<Instant> touchHeartbeat) {
        final var jobRunId = jobRunContext.jobRun().id();

        if (!state.workerThread().isAlive()) {
            logger.warn(
                    "Worker thread for job run {} is no longer alive without having completed"
                            + " normally; its thread (pool) was likely stopped. Marking it cancelled.",
                    jobRunId);
            jobService.updateJobRunState(
                    jobRunContext.jobRun(), JobRunState.CANCELLED, Instant.now());
            return false;
        }

        final var cpuTime = state.currentCpuTime();
        final var madeProgress = !state.cpuTimeSupported() || cpuTime != state.lastCpuTime();
        state = state.checkedIn(cpuTime);
        if (!madeProgress) {
            logger.debug(
                    "Worker thread for job run {} made no CPU progress since the last check-in;"
                            + " not touching its heartbeat.",
                    jobRunId);
            return true;
        }

        touchHeartbeat.accept(Instant.now());
        return true;
    }
}
