package ch.ubique.backend.job.model;

import ch.ubique.backend.job.model.triggers.TriggerType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobRunDatabaseSubmissionArguments(boolean forceRun, @Nullable Object additionalData) {

    @NotNull
    public JobRunArguments toJobRunArguments() {
        return new JobRunArguments(
                forceRun, TriggerType.DATABASE_SUBMISSION, null, null, additionalData);
    }
}
