package ch.ubique.backend.job.model;

import jakarta.annotation.Nullable;

public record JobRunArguments(boolean forceRun, @Nullable Object additionalData) {

    public static JobRunArguments DEFAULT = new JobRunArguments(false);

    public JobRunArguments(final boolean forceRun) {
        this(forceRun, null);
    }
}
