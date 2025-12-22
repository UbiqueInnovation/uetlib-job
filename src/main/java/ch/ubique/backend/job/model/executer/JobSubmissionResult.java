package ch.ubique.backend.job.model.executer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface JobSubmissionResult
        permits SuccessfulSubmissionResult, UnsuccessfulSubmissionResult {

    @JsonProperty(required = true)
    @NotNull
    JobSubmissionStatus status();
}
