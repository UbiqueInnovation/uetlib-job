package ch.uetlib.job.model;

import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.model.runner.conditions.JobRunnerTaint;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

public record JobQueuedRun(
        int jobQueuedRunId,
        @Nullable Integer priority,
        int jobId,
        String versionTag,
        JobRunQueueState queueState,
        @Nullable Integer jobRunId,
        @Nullable String runner,
        @Nullable Instant lastHeartbeat,
        Instant createdAt,
        Instant updatedAt,
        Set<JobRunnerTaint> taints,
        JobRunArguments arguments) {

    /**
     * Whether {@link #lastHeartbeat} is older than {@code timeout} (or missing entirely) as of
     * {@code now}. The single definition of "stale" - used both by {@code JobQueueWatcherService}
     * to decide whether to abandon a {@link JobRunQueueState#RUNNING} row, and by the status API to
     * surface the same verdict to callers without them having to duplicate the math or know the
     * per-job {@code HEARTBEAT_TIMEOUT} override.
     */
    public boolean isHeartbeatStale(final Duration timeout, final Instant now) {
        return lastHeartbeat == null || lastHeartbeat.isBefore(now.minus(timeout));
    }
}
