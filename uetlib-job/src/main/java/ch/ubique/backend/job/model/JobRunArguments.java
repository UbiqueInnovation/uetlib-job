package ch.ubique.backend.job.model;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

public record JobRunArguments(
        boolean forceRun, @NotNull TriggerContext triggerContext, @Nullable Object additionalData) {

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
