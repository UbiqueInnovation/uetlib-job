package ch.ubique.backend.job.model.client;

import ch.ubique.backend.core.utils.StringFormattingUtils;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobRunInfo(
        @NotNull int id,
        @NotNull int jobId,
        @NotNull int jobVersion,
        @NotNull JobRunState state,
        @NotNull Instant startedAt,
        Instant finishedAt,
        @NotNull String duration) {

    public static JobRunInfo from(final JobRun jobRun) {
        return new JobRunInfo(
                jobRun.id(),
                jobRun.jobId(),
                jobRun.jobVersion(),
                jobRun.state(),
                jobRun.startedAt(),
                jobRun.finishedAt(),
                StringFormattingUtils.formatDuration(jobRun.startedAt(), jobRun.finishedAt()));
    }
}
