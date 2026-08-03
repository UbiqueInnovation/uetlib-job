package ch.uetlib.job.model;

import ch.uetlib.job.model.queue.JobRunQueueState;

import org.jspecify.annotations.Nullable;

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
        Set<String> taints,
        JobRunArguments arguments) {}
