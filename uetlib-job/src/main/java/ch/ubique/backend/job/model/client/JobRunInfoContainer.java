package ch.ubique.backend.job.model.client;

import ch.ubique.backend.job.model.JobRun;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobRunInfoContainer(@NotNull JobRunInfo jobRun, @NotNull String jobName) {

    public static JobRunInfoContainer from(final JobRun jobRun, final String jobName) {
        return new JobRunInfoContainer(JobRunInfo.from(jobRun), jobName);
    }
}
