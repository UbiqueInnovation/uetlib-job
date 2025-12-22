package ch.ubique.backend.job.model.client;

import ch.ubique.backend.core.utils.StringFormattingUtils;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.TriggerContext;
import ch.ubique.backend.job.model.triggers.TriggerType;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobRunInfo(
        @Positive int id,
        @Positive int jobId,
        @NotNull JobRunState state,
        @NotNull @PastOrPresent Instant startedAt,
        @PastOrPresent Instant finishedAt,
        @NotNull String duration,
        @NotNull String versionTag,
        @Nullable TriggerType triggerType,
        @Nullable TriggerContext triggerContext,
        boolean hasFiles) {

    public static JobRunInfo from(final JobRun jobRun, final boolean hasFiles) {
        return new JobRunInfo(
                jobRun.id(),
                jobRun.jobId(),
                jobRun.latestState(),
                jobRun.startedAt(),
                jobRun.finishedAt(),
                StringFormattingUtils.formatDuration(jobRun.startedAt(), jobRun.finishedAt()),
                jobRun.versionTag(),
                jobRun.triggerType(),
                Optional.ofNullable(jobRun.jobRunArguments())
                        .map(JobRunArguments::triggerContext)
                        .orElse(null),
                hasFiles);
    }
}
