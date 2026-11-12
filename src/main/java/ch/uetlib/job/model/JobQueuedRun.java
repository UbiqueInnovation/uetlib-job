package ch.uetlib.job.model;

import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.model.runner.conditions.JobRunnerTaint;

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
        Instant createdAt,
        Instant updatedAt,
        Set<JobRunnerTaint> taints,
        JobRunArguments arguments) {}
