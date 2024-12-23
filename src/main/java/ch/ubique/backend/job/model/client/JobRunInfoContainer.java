package ch.ubique.backend.job.model.client;


import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;

/**
 * @param jobRun JobRunInfo
 * @param job JobInfo
 * @param jobName deprecated, use {@link #job().jobName()}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobRunInfoContainer(
        @NotNull JobRunInfo jobRun, @NotNull JobInfo job, @NotNull String jobName) {

    public static JobRunInfoContainer from(final JobRunInfo jobRunInfo, final JobInfo jobInfo) {
        return new JobRunInfoContainer(jobRunInfo, jobInfo, jobInfo.name());
    }
}
