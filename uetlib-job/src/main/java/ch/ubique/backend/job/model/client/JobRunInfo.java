package ch.ubique.backend.job.model.client;

import ch.ubique.backend.core.utils.StringFormattingUtils;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

import javax.validation.constraints.NotNull;

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
                jobRun.getId(),
                jobRun.getJobId(),
                jobRun.getJobVersion(),
                jobRun.getState(),
                jobRun.getStartedAt(),
                jobRun.getFinishedAt(),
                StringFormattingUtils.formatDuration(
                        jobRun.getStartedAt(), jobRun.getFinishedAt()));
    }
}
