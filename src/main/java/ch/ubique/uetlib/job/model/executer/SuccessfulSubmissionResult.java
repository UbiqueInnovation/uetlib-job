package ch.ubique.uetlib.job.model.executer;

import ch.ubique.uetlib.job.model.JobRunContext;
import ch.ubique.uetlib.job.model.properties.enums.JobRunnerKey;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SuccessfulSubmissionResult(
        @JsonProperty(required = true) @NotNull JobRunnerKey runnerKey,
        @JsonProperty(required = true) @NotNull @Valid JobRunContext jobRunContext,
        @JsonProperty(required = true) int jobRunId)
        implements JobSubmissionResult {

    @Override
    public JobSubmissionStatus status() {
        return JobSubmissionStatus.SUBMITTED;
    }
}
