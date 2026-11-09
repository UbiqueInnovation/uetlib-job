package ch.uetlib.job.commands;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

public record JobApplicationJobToStartArgs(
        String jobClassName,
        @Nullable Integer jobRunId,
        boolean forceRun,
        @Nullable String additionalDataString,
        @Nullable String additionalDataClassName) {

    public Optional<Integer> optionalJobRunId() {
        return Optional.ofNullable(jobRunId);
    }
}
