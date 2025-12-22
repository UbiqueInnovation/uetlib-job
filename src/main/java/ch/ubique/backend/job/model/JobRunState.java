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
    CANCELLED,
    SKIPPED,
    SCHEDULED,
    ACKNOWLEDGED;

    public static final Collection<JobRunState> RUNNING_STATES = Set.of(BUSY, SCHEDULED);
    public static final Collection<JobRunState> PROBLEM_STATES =
            Set.of(UNFINISHED, CANCELLED, WARNING, ERROR);
    public static final Collection<JobRunState> SUCCESS_STATES =
            Set.of(SKIPPED, ACKNOWLEDGED, DONE);

    @SuppressWarnings("java:S2386")
    public static final Collection<JobRunState> ALL_EXCEPT_RUNNING_STATES =
            allExcept(RUNNING_STATES);

    public static Set<JobRunState> allExcept(final Collection<JobRunState> excludedStates) {
        return SetUtils.difference(Set.of(values()), Set.copyOf(excludedStates));
    }

    public boolean isRunning() {
        return RUNNING_STATES.contains(this);
    }

    public boolean isSuccess() {
        return SUCCESS_STATES.contains(this);
    }

    public boolean isProblem() {
        return PROBLEM_STATES.contains(this);
    }

    public boolean isAcknowledgeable() {
        return isProblem();
    }
}
