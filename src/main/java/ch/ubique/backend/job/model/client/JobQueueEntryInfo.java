package ch.ubique.backend.job.model.client;

import ch.ubique.backend.job.model.JobQueuedRun;
import ch.ubique.backend.job.model.queue.JobRunQueueState;
import ch.ubique.backend.job.model.runner.conditions.JobRunnerTaint;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobQueueEntryInfo(
        @JsonProperty(required = true) @Positive int id,
        @JsonProperty(required = true) @Positive int jobId,
        @Nullable String jobName,
        @Nullable Integer priority,
        @JsonProperty(required = true) @NotBlank String versionTag,
        @JsonProperty(required = true) @NotNull JobRunQueueState state,
        @Nullable Integer jobRunId,
        @Nullable String runner,
        @Nullable Instant lastHeartbeat,
        @Nullable Boolean heartbeatStale,
        @JsonProperty(required = true) @NotNull Instant createdAt,
        @JsonProperty(required = true) @NotNull Instant updatedAt,
        @JsonProperty(required = true) @NotNull Set<JobRunnerTaint> taints) {

    /**
     * @param heartbeatTimeout the effective {@code HEARTBEAT_TIMEOUT} for this run's job; only used
     *     (and {@link JobQueuedRun#isHeartbeatStale} only meaningful) while {@code run} is {@link
     *     JobRunQueueState#RUNNING} - {@code null} otherwise, since a queued-but-not-yet-claimed row
     *     has no heartbeat to judge.
     */
    public static JobQueueEntryInfo from(
            final JobQueuedRun run,
            @Nullable final String jobName,
            @Nullable final Duration heartbeatTimeout) {
        final var heartbeatStale =
                run.queueState() == JobRunQueueState.RUNNING && heartbeatTimeout != null
                        ? run.isHeartbeatStale(heartbeatTimeout, Instant.now())
                        : null;
        return new JobQueueEntryInfo(
                run.jobQueuedRunId(),
                run.jobId(),
                jobName,
                run.priority(),
                run.versionTag(),
                run.queueState(),
                run.jobRunId(),
                run.runner(),
                run.lastHeartbeat(),
                heartbeatStale,
                run.createdAt(),
                run.updatedAt(),
                run.taints());
    }
}
