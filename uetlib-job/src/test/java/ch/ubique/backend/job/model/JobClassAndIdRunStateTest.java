package ch.ubique.backend.job.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Set;

class JobClassAndIdRunStateTest {

    @Test
    void allExceptRunning() {
        assertEquals(JobRunState.RUNNING, Set.of(JobRunState.SCHEDULED, JobRunState.BUSY));
        assertTrue(JobRunState.RUNNING.stream().allMatch(JobRunState::isRunning));
        assertTrue(JobRunState.ALL_EXCEPT_RUNNING.stream().noneMatch(JobRunState::isRunning));

        assertEquals(
                Set.of(
                        JobRunState.DONE,
                        JobRunState.WARNING,
                        JobRunState.ERROR,
                        JobRunState.NONE,
                        JobRunState.UNFINISHED,
                        JobRunState.SKIPPED),
                JobRunState.allExcept(JobRunState.RUNNING));
        assertEquals(JobRunState.ALL_EXCEPT_RUNNING, JobRunState.allExcept(JobRunState.RUNNING));

        assertEquals(
                Set.of(JobRunState.SCHEDULED, JobRunState.BUSY),
                JobRunState.allExcept(JobRunState.ALL_EXCEPT_RUNNING));
    }

    @Test
    void allExcept() {
        assertEquals(Set.of(JobRunState.values()), JobRunState.allExcept(Set.of()));
        assertEquals(Set.of(), JobRunState.allExcept(Set.of(JobRunState.values())));
    }
}
