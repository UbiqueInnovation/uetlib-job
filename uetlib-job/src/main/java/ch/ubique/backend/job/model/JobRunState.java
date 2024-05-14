package ch.ubique.backend.job.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public enum JobRunState {
    BUSY,
    DONE,
    WARNING,
    ERROR,
    UNFINISHED,
    NONE,
    SKIPPED,
    SCHEDULED;

    public boolean isRunning() {
        return RUNNING.contains(this);
    }

    public static final Collection<JobRunState> RUNNING = Set.of(BUSY, SCHEDULED);

    public static final Collection<JobRunState> ALL_EXCEPT_RUNNING = allExcept(RUNNING);

    public static Set<JobRunState> allExcept(final Collection<JobRunState> excludedStates) {
        return Arrays.stream(values())
                .filter(state -> !excludedStates.contains(state))
                .collect(Collectors.toUnmodifiableSet());
    }
}
