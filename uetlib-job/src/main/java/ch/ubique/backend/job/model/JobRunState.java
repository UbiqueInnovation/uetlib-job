package ch.ubique.backend.job.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

public enum JobRunState {
    BUSY,
    DONE,
    WARNING,
    ERROR,
    UNFINISHED,
    NONE,
    SKIPPED,
    SCHEDULED;

    public static JobRunState[] allExceptRunning() {
        return allExcept(Set.of(JobRunState.BUSY, JobRunState.SCHEDULED));
    }

    public static JobRunState[] allExcept(final Collection<JobRunState> excludedStates) {
        return Arrays.stream(values())
                .filter(state -> !excludedStates.contains(state))
                .toArray(JobRunState[]::new);
    }
}
