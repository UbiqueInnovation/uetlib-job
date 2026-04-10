package ch.ubique.backend.job.model;

import ch.ubique.backend.job.model.triggers.TriggerType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobRunDatabaseSubmissionArguments(
        boolean forceRun, @Nullable JobRunArgumentsAdditionalData additionalData) {

    @NotNull
    public JobRunArguments toJobRunArguments() {
        return new JobRunArguments(
                forceRun, TriggerType.DATABASE_SUBMISSION, null, null, additionalData);
    }
}
