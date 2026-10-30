package ch.ubique.uetlib.job.commands;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

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
