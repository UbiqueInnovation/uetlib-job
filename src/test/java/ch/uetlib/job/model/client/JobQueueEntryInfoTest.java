package ch.uetlib.job.model.client;

import static org.junit.jupiter.api.Assertions.*;

import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.queue.JobRunQueueState;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Covers all four combinations of {@link JobQueueEntryInfo#from}'s {@code state == RUNNING &&
 * heartbeatTimeout != null} guard - only "RUNNING with a timeout" and "not RUNNING" were tested
 * via {@code JobControllerServiceTest}; "RUNNING but no timeout available" (e.g. the run's job was
 * deleted, so {@code JobControllerService} can't look up its {@code HEARTBEAT_TIMEOUT}) wasn't.
 */
class JobQueueEntryInfoTest {

    @Test
    void runningWithATimeoutComputesTheStaleVerdict() {
        final var run = queuedRun(JobRunQueueState.RUNNING, Instant.now().minusSeconds(3600));

        final var info = JobQueueEntryInfo.from(run, "job", Duration.ofMinutes(5));

        assertEquals(Boolean.TRUE, info.heartbeatStale());
    }

    @Test
    void runningWithoutATimeoutLeavesTheVerdictUnset() {
        final var run = queuedRun(JobRunQueueState.RUNNING, Instant.now().minusSeconds(3600));

        final var info = JobQueueEntryInfo.from(run, "job", null);

        assertNull(info.heartbeatStale());
    }

    @Test
    void notRunningLeavesTheVerdictUnsetEvenWithATimeout() {
        final var run = queuedRun(JobRunQueueState.QUEUED, null);

        final var info = JobQueueEntryInfo.from(run, "job", Duration.ofMinutes(5));

        assertNull(info.heartbeatStale());
    }

    @Test
    void notRunningWithoutATimeoutLeavesTheVerdictUnset() {
        final var run = queuedRun(JobRunQueueState.CANCELLED, null);

        final var info = JobQueueEntryInfo.from(run, "job", null);

        assertNull(info.heartbeatStale());
    }

    private static JobQueuedRun queuedRun(
            final JobRunQueueState state, final Instant lastHeartbeat) {
        final var now = Instant.now();
        return new JobQueuedRun(
                1, null, 1, "1.0.0", state, null, "runner", lastHeartbeat, now, now, Set.of(),
                new JobRunArguments());
    }
}
