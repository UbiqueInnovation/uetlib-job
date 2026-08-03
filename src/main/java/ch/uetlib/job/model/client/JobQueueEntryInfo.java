package ch.uetlib.job.model.client;

import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.queue.JobRunQueueState;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

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
        @JsonProperty(required = true) @NotNull Instant createdAt,
        @JsonProperty(required = true) @NotNull Instant updatedAt,
        @JsonProperty(required = true) @NotNull Set<String> taints) {

    public static JobQueueEntryInfo from(final JobQueuedRun run, @Nullable final String jobName) {
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
                run.createdAt(),
                run.updatedAt(),
                run.taints());
    }
}
