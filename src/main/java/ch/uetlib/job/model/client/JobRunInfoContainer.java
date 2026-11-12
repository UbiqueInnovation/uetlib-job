package ch.uetlib.job.model.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

/**
 * @param jobRun JobRunInfo
 * @param job JobInfo
 * @param jobName deprecated, use {@link #job()}.{@link JobInfo#name()}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobRunInfoContainer(
        @JsonProperty(required = true) @NotNull JobRunInfo jobRun,
        @JsonProperty(required = true) @NotNull JobInfo job,
        @JsonProperty(required = true) @NotNull String jobName) {

    public static JobRunInfoContainer from(final JobRunInfo jobRunInfo, final JobInfo jobInfo) {
        return new JobRunInfoContainer(jobRunInfo, jobInfo, jobInfo.name());
    }
}
