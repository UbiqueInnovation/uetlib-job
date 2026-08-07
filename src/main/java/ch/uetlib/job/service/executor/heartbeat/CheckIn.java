package ch.uetlib.job.service.executor.heartbeat;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Something a {@link RunHeartbeat} can verify each tick to decide whether the run is still alive,
 * and - if so - touch its heartbeat. Swappable per {@link RunHeartbeat#onLocalExecutionFinished},
 * since what "still alive" even means differs depending on where the run's actual work is
 * happening.
 */
@FunctionalInterface
interface CheckIn {
    /**
     * @param touchHeartbeat call to record proof of life at a given instant
     * @return whether the run is still going and should be checked in on again
     */
    boolean checkIn(Consumer<Instant> touchHeartbeat);
}
