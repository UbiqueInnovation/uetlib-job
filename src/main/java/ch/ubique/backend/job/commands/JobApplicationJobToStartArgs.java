package ch.ubique.backend.job.commands;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public record JobApplicationJobToStartArgs(
        @NonNull String jobClassName,
        @Nullable Integer jobRunId,
        boolean forceRun,
        @Nullable String additionalDataString,
        @Nullable String additionalDataClassName) {

    public Optional<Integer> optionalJobRunId() {
        return Optional.ofNullable(jobRunId);
    }
}
