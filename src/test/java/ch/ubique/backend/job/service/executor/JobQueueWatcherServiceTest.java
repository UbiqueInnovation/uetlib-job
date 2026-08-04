package ch.ubique.backend.job.service.executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobQueuedRun;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobProperties;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.queue.JobRunQueueState;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.testjob.TestJob;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Covers both watcher passes: reclaiming abandoned {@code CLAIMED} rows on a per-job timeout, and
 * reconciling {@code RUNNING} rows against their linked {@link JobRun}'s actual state.
 */
class JobQueueWatcherServiceTest {

    private static final Duration DEFAULT_CLAIM_TIMEOUT = Duration.ofMinutes(2);

    private final JobService jobService = mock(JobService.class);
    private final JobDataService jobDataService = mock(JobDataService.class);

    private final JobQueueWatcherService service =
            new JobQueueWatcherService(jobService, jobDataService, DEFAULT_CLAIM_TIMEOUT);

    @Test
    void resetAbandonedClaimsUsesDefaultTimeoutWhenNoPropertyOverride() {
        final var job = new TestJob();
        when(jobService.getAllJobs()).thenReturn(List.of(job));
        when(jobService.findJobId(TestJob.class)).thenReturn(Optional.of(7));
        when(jobService.getProperties(job)).thenReturn(new JobProperties(List.of()));
        when(jobDataService.resetStaleClaims(anyMap())).thenReturn(0);

        service.resetAbandonedClaims();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Map<Integer, Instant>> captor = ArgumentCaptor.forClass(Map.class);
        verify(jobDataService).resetStaleClaims(captor.capture());
        assertEquals(Set.of(7), captor.getValue().keySet());
        final var staleBefore = captor.getValue().get(7);
        final var expected = Instant.now().minus(DEFAULT_CLAIM_TIMEOUT);
        assertTrue(Duration.between(staleBefore, expected).abs().compareTo(Duration.ofSeconds(5)) < 0);
    }

    @Test
    void reconcileDeletesTheQueuedRowOnceItsLinkedJobRunIsNoLongerRunning() {
        final var queuedRun = queuedRun(1, 42);
        when(jobDataService.findQueuedRunsByState(JobRunQueueState.RUNNING))
                .thenReturn(List.of(queuedRun));
        when(jobDataService.findJobRunById(42)).thenReturn(Optional.of(jobRun(JobRunState.DONE)));

        service.reconcileRunningQueuedRuns();

        verify(jobDataService).deleteQueuedRunById(1);
        verify(jobDataService, never()).touchQueuedRunHeartbeat(anyInt());
    }

    @Test
    void reconcileDeletesTheQueuedRowWhenTheLinkedJobRunNoLongerExists() {
        final var queuedRun = queuedRun(1, 42);
        when(jobDataService.findQueuedRunsByState(JobRunQueueState.RUNNING))
                .thenReturn(List.of(queuedRun));
        when(jobDataService.findJobRunById(42)).thenReturn(Optional.empty());

        service.reconcileRunningQueuedRuns();

        verify(jobDataService).deleteQueuedRunById(1);
    }

    @Test
    void reconcileTouchesTheHeartbeatWhileTheLinkedJobRunIsStillRunning() {
        final var queuedRun = queuedRun(1, 42);
        when(jobDataService.findQueuedRunsByState(JobRunQueueState.RUNNING))
                .thenReturn(List.of(queuedRun));
        when(jobDataService.findJobRunById(42)).thenReturn(Optional.of(jobRun(JobRunState.BUSY)));

        service.reconcileRunningQueuedRuns();

        verify(jobDataService).touchQueuedRunHeartbeat(1);
        verify(jobDataService, never()).deleteQueuedRunById(anyInt());
    }

    @Test
    void reconcileDefensivelyResetsARunningRowWithNoLinkedJobRunId() {
        final var queuedRun = queuedRun(1, null);
        when(jobDataService.findQueuedRunsByState(JobRunQueueState.RUNNING))
                .thenReturn(List.of(queuedRun));

        service.reconcileRunningQueuedRuns();

        verify(jobDataService).resetQueuedRunById(1);
        verify(jobDataService, never()).findJobRunById(anyInt());
    }

    private static JobQueuedRun queuedRun(final int id, final Integer jobRunId) {
        final var now = Instant.now();
        return new JobQueuedRun(
                id,
                null,
                1,
                "1.0.0",
                JobRunQueueState.RUNNING,
                jobRunId,
                "runner",
                now,
                now,
                now,
                Set.of(),
                new JobRunArguments());
    }

    private static JobRun jobRun(final JobRunState state) {
        return new JobRun(
                42, 1, "1.0.0", Instant.now(), null, state, JobRunnerKey.CURRENT, null, null);
    }
}
