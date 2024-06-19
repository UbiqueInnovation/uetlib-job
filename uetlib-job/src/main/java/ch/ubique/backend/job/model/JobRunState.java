package ch.ubique.backend.job.model;

import org.apache.commons.collections4.SetUtils;

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

    public static final Collection<JobRunState> RUNNING_STATES = Set.of(BUSY, SCHEDULED);
    public static final Collection<JobRunState> ALL_EXCEPT_RUNNING_STATES =
            allExcept(RUNNING_STATES);

    public static Set<JobRunState> allExcept(final Collection<JobRunState> excludedStates) {
        return SetUtils.difference(Set.of(values()), Set.copyOf(excludedStates));
    }

    public boolean isRunning() {
        return RUNNING_STATES.contains(this);
    }
}
