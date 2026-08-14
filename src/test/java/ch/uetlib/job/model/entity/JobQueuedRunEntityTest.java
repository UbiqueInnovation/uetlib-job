package ch.uetlib.job.model.entity;

import static org.junit.jupiter.api.Assertions.*;

import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.model.runner.conditions.JobRunnerTaint;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

class JobQueuedRunEntityTest {

    @Test
    void theQueuedConstructorStartsInQueuedStateWithoutARunYet() {
        final var entity =
                new JobQueuedRunEntity(
                        1, 3, "1.0.0", new JobRunArguments(), Set.of(new JobRunnerTaint("gpu")));

        ReflectionTestUtils.setField(entity, "jobQueuedRunId", 42);
        ReflectionTestUtils.setField(entity, "createdAt", java.time.Instant.now());
        ReflectionTestUtils.setField(entity, "updatedAt", java.time.Instant.now());
        final var run = entity.toJobQueuedRun();

        assertEquals(JobRunQueueState.QUEUED, run.queueState());
        assertNull(run.jobRunId());
        assertEquals(3, run.priority());
        assertEquals(Set.of(new JobRunnerTaint("gpu")), run.taints());
    }

    @Test
    void theDirectDispatchConstructorStartsAsRunningWithAHeartbeat() {
        final var entity =
                new JobQueuedRunEntity(
                        1, "1.0.0", new JobRunArguments(), Set.of(), 99, "runner-a");

        ReflectionTestUtils.setField(entity, "jobQueuedRunId", 43);
        ReflectionTestUtils.setField(entity, "createdAt", java.time.Instant.now());
        ReflectionTestUtils.setField(entity, "updatedAt", java.time.Instant.now());
        final var run = entity.toJobQueuedRun();

        assertEquals(JobRunQueueState.RUNNING, run.queueState());
        assertEquals(99, run.jobRunId());
        assertEquals("runner-a", run.runner());
        assertNotNull(run.lastHeartbeat());
        assertNull(run.priority());
    }

    @Test
    void setRunIdTransitionsToRunningAndRefreshesTheHeartbeat() {
        final var entity =
                new JobQueuedRunEntity(1, null, "1.0.0", new JobRunArguments(), Set.of());
        ReflectionTestUtils.setField(entity, "jobQueuedRunId", 44);
        ReflectionTestUtils.setField(entity, "createdAt", java.time.Instant.now());
        ReflectionTestUtils.setField(entity, "updatedAt", java.time.Instant.now());

        entity.setRunId(7);
        final var run = entity.toJobQueuedRun();

        assertEquals(JobRunQueueState.RUNNING, run.queueState());
        assertEquals(7, run.jobRunId());
        assertNotNull(run.lastHeartbeat());
    }
}
