package ch.ubique.backend.job.service.executor.heartbeat;

import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.service.JobService;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * The local worker thread has already finished its part - it handed the run off to something else
 * and returned {@link JobRunState#SCHEDULED} - so there's nothing left here to check for progress.
 * Instead, this just watches the run's own state: as long as it's still {@link
 * JobRunState#SCHEDULED}, something else is presumably still watching it (a Kubernetes pod watcher,
 * an external callback), so the heartbeat is touched on trust; as soon as it moves on, check-ins
 * stop.
 *
 * <p>How long a run may sit unattended in SCHEDULED before that itself counts as abandoned is a
 * separate, duration-based concern already covered by {@code JobTimeoutWatcher}'s {@code
 * MAX_DURATION_IN_SCHEDULED_STATE}, so it's deliberately not duplicated here.
 */
final class ScheduledElsewhereCheckIn implements CheckIn {

    private final JobService jobService;
    private final int jobRunId;

    ScheduledElsewhereCheckIn(final JobService jobService, final int jobRunId) {
        this.jobService = jobService;
        this.jobRunId = jobRunId;
    }

    @Override
    public boolean checkIn(final Consumer<Instant> touchHeartbeat) {
        final var current = jobService.getJobRunMaybe(jobRunId);
        if (current.isEmpty() || current.get().latestState() != JobRunState.SCHEDULED) {
            return false;
        }
        touchHeartbeat.accept(Instant.now());
        return true;
    }
}
