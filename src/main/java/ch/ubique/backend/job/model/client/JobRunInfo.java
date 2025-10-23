package ch.ubique.backend.job.model.client;

import ch.ubique.backend.core.utils.StringFormattingUtils;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobRunInfo(
        @Positive int id,
        @Positive int jobId,
        @NotNull JobRunState state,
        @NotNull @PastOrPresent Instant startedAt,
        @PastOrPresent Instant finishedAt,
        @NotNull String duration,
        @NotNull String versionTag,
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
                hasFiles);
    }
}
