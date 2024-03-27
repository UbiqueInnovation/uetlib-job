package ch.ubique.backend.job.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobRunArguments(
        boolean forceRun, @NotNull @Valid TriggerContext triggerContext, @Nullable Object additionalData) {

    public static JobRunArguments DEFAULT = new JobRunArguments(false);

    public JobRunArguments() {
        this(false);
    }

    public JobRunArguments(final boolean forceRun) {
        this(forceRun, null);
    }

    public JobRunArguments(final boolean forceRun, @Nullable final Object additionalData) {
        this(forceRun, new EmptyTriggerContext(), additionalData);
    }
}
