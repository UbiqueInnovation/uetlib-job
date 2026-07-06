package ch.ubique.backend.job.model.client;

import ch.ubique.backend.core.utils.StringFormattingUtils;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.TriggerContext;
import ch.ubique.backend.job.model.triggers.TriggerType;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobRunInfo(
        @JsonProperty(required = true) @Positive int id,
        @JsonProperty(required = true) @Positive int jobId,
        @JsonProperty(required = true) @NotNull JobRunState state,
        @JsonProperty(required = true) @NotNull @PastOrPresent Instant startedAt,
        @PastOrPresent @Nullable Instant finishedAt,
        @JsonProperty(required = true) @NotNull String duration,
        @JsonProperty(required = true) @NotNull String versionTag,
        @Nullable TriggerType triggerType,
        @Nullable TriggerContext triggerContext,
        @JsonProperty(required = true) boolean hasFiles) {

    public static JobRunInfo from(final JobRun jobRun, final boolean hasFiles) {
        return new JobRunInfo(
                jobRun.id(),
                jobRun.jobId(),
                jobRun.latestState(),
                jobRun.startedAt(),
                jobRun.finishedAt(),
                StringFormattingUtils.formatDuration(
                        jobRun.startedAt(),
                        java.util.Objects.requireNonNullElse(jobRun.finishedAt(), Instant.now())),
                jobRun.versionTag(),
                jobRun.triggerType(),
                Optional.ofNullable(jobRun.jobRunArguments())
                        .map(JobRunArguments::triggerContext)
                        .orElse(null),
                hasFiles);
    }
}
