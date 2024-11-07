package ch.ubique.backend.job.commands;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.util.Optional;

public record JobApplicationJobToStartArgs(
        @NotNull String jobClassName,
        @Nullable Integer jobRunId,
        boolean forceRun,
        @Nullable String additionalDataString,
        @Nullable String additionalDataClassName) {

    public Optional<Integer> optionalJobRunId() {
        return Optional.ofNullable(jobRunId);
    }
}
