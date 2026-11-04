package ch.uetlib.job.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Set;

class JobClassAndIdRunStateTest {

    @Test
    void allExceptRunning() {
        assertEquals(JobRunState.RUNNING_STATES, Set.of(JobRunState.SCHEDULED, JobRunState.BUSY));
        assertTrue(JobRunState.RUNNING_STATES.stream().allMatch(JobRunState::isRunning));
        assertTrue(
                JobRunState.ALL_EXCEPT_RUNNING_STATES.stream().noneMatch(JobRunState::isRunning));

        assertEquals(
                Set.of(
                        JobRunState.DONE,
                        JobRunState.WARNING,
                        JobRunState.ERROR,
                        JobRunState.UNFINISHED,
                        JobRunState.SCHEDULING_FAILED,
                        JobRunState.CANCELLED,
                        JobRunState.ACKNOWLEDGED,
                        JobRunState.SKIPPED),
                JobRunState.allExcept(JobRunState.RUNNING_STATES));
        assertEquals(
                JobRunState.ALL_EXCEPT_RUNNING_STATES,
                JobRunState.allExcept(JobRunState.RUNNING_STATES));

        assertEquals(
                Set.of(JobRunState.SCHEDULED, JobRunState.BUSY),
                JobRunState.allExcept(JobRunState.ALL_EXCEPT_RUNNING_STATES));
    }

    @Test
    void allExcept() {
        assertEquals(Set.of(JobRunState.values()), JobRunState.allExcept(Set.of()));
        assertEquals(Set.of(), JobRunState.allExcept(Set.of(JobRunState.values())));
    }
}
