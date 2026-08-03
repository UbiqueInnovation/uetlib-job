package ch.ubique.backend.job.model;

import ch.ubique.backend.job.model.queue.JobRunQueueState;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Set;

public record JobQueuedRun(
        int jobQueuedRunId,
        int jobId,
        String versionTag,
        JobRunQueueState queueState,
        @Nullable Integer jobRunId,
        @Nullable String runner,
        @Nullable Instant lastHeartbeat,
        Set<String> taints,
        JobRunArguments arguments) {}
