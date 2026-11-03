package ch.ubique.uetlib.job.model;

import ch.ubique.uetlib.job.model.triggers.TriggerType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobRunDatabaseSubmissionArguments(
        @JsonProperty(required = true) boolean forceRun,
        @Nullable JobRunArgumentsAdditionalData additionalData) {

    public JobRunArguments toJobRunArguments() {
        return new JobRunArguments(
                forceRun, TriggerType.DATABASE_SUBMISSION, null, null, additionalData);
    }
}
