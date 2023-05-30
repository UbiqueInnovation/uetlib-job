package ch.ubique.backend.job.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Set;

class JobRunStateTest {

    @Test
    void allExceptRunning() {
        assertEquals(
                Set.of(
                        JobRunState.DONE,
                        JobRunState.WARNING,
                        JobRunState.ERROR,
                        JobRunState.NONE,
                        JobRunState.UNFINISHED,
                        JobRunState.SKIPPED),
                Set.of(JobRunState.allExceptRunning()));

        assertEquals(
                Set.of(JobRunState.SCHEDULED, JobRunState.BUSY),
                Set.of(JobRunState.allExcept(Set.of(JobRunState.allExceptRunning()))));
    }

    @Test
    void allExcept() {
        assertEquals(Set.of(JobRunState.values()), Set.of(JobRunState.allExcept(Set.of())));
        assertEquals(Set.of(), Set.of(JobRunState.allExcept(Set.of(JobRunState.values()))));
    }
}
