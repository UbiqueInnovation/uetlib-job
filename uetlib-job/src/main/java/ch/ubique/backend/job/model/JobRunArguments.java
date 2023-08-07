package ch.ubique.backend.job.model;

import jakarta.annotation.Nullable;

public record JobRunArguments(boolean forceRun) {

    public static JobRunArguments DEFAULT = new JobRunArguments(false);
}
