package ch.uetlib.job.service.executor;

import static ch.uetlib.job.model.properties.JobPropertyDefinitions.HEARTBEAT_TIMEOUT;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.properties.JobProperty;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.executor.heartbeat.HeartbeatRegistry;
import ch.uetlib.job.testjob.TestJob;

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
    private static final Duration DEFAULT_HEARTBEAT_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration DEFAULT_QUEUE_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration DEFAULT_CANCELLED_QUEUE_RETENTION = Duration.ofDays(7);

    private final JobService jobService = mock(JobService.class);
    private final JobDataService jobDataService = mock(JobDataService.class);
    private final HeartbeatRegistry heartbeatRegistry = mock(HeartbeatRegistry.class);

    private final JobQueueWatcherService service =
            new JobQueueWatcherService(jobService, jobDataService, heartbeatRegistry);

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
        assertTrue(
                Duration.between(staleBefore, expected).abs().compareTo(Duration.ofSeconds(5)) < 0);
    }

    @Test
    void expireUnclaimableQueuedRunsUsesDefaultTimeoutWhenNoPropertyOverride() {
        final var job = new TestJob();
        when(jobService.getAllJobs()).thenReturn(List.of(job));
        when(jobService.findJobId(TestJob.class)).thenReturn(Optional.of(7));
        when(jobService.getProperties(job)).thenReturn(new JobProperties(List.of()));
        when(jobDataService.expireStaleQueuedRuns(anyMap())).thenReturn(0);

        service.expireUnclaimableQueuedRuns();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Map<Integer, Instant>> captor = ArgumentCaptor.forClass(Map.class);
        verify(jobDataService).expireStaleQueuedRuns(captor.capture());
        assertEquals(Set.of(7), captor.getValue().keySet());
        final var staleBefore = captor.getValue().get(7);
        final var expected = Instant.now().minus(DEFAULT_QUEUE_TIMEOUT);
        assertTrue(
                Duration.between(staleBefore, expected).abs().compareTo(Duration.ofSeconds(5)) < 0);
    }

    @Test
    void deleteOldCancelledQueuedRunsUsesDefaultRetentionWhenNoPropertyOverride() {
        final var job = new TestJob();
        when(jobService.getAllJobs()).thenReturn(List.of(job));
        when(jobService.findJobId(TestJob.class)).thenReturn(Optional.of(7));
        when(jobService.getProperties(job)).thenReturn(new JobProperties(List.of()));
        when(jobDataService.deleteOldCancelledQueuedRuns(anyMap())).thenReturn(0);

        service.deleteOldCancelledQueuedRuns();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Map<Integer, Instant>> captor = ArgumentCaptor.forClass(Map.class);
        verify(jobDataService).deleteOldCancelledQueuedRuns(captor.capture());
        assertEquals(Set.of(7), captor.getValue().keySet());
        final var retainAfter = captor.getValue().get(7);
        final var expected = Instant.now().minus(DEFAULT_CANCELLED_QUEUE_RETENTION);
        assertTrue(
                Duration.between(retainAfter, expected).abs().compareTo(Duration.ofSeconds(5)) < 0);
    }

    @Test
    void reconcileDeletesTheQueuedRowOnceItsLinkedJobRunIsNoLongerRunning() {
        final var queuedRun = queuedRun(1, 42);
        when(jobDataService.findQueuedRunsByState(JobRunQueueState.RUNNING))
                .thenReturn(List.of(queuedRun));
        when(jobDataService.findJobRunById(42)).thenReturn(Optional.of(jobRun(JobRunState.DONE)));

        service.reconcileRunningQueuedRuns();

        verify(jobDataService).deleteQueuedRunById(1);
        verify(heartbeatRegistry).stop(42);
    }

    @Test
    void reconcileDeletesTheQueuedRowWhenTheLinkedJobRunNoLongerExists() {
        final var queuedRun = queuedRun(1, 42);
        when(jobDataService.findQueuedRunsByState(JobRunQueueState.RUNNING))
                .thenReturn(List.of(queuedRun));
        when(jobDataService.findJobRunById(42)).thenReturn(Optional.empty());

        service.reconcileRunningQueuedRuns();

        verify(jobDataService).deleteQueuedRunById(1);
        verify(heartbeatRegistry).stop(42);
    }

    @Test
    void reconcileLeavesARunningRowAloneWhileItsHeartbeatIsFresh() {
        stubHeartbeatTimeout(1, DEFAULT_HEARTBEAT_TIMEOUT);
        final var queuedRun = queuedRun(1, 42, Instant.now());
        when(jobDataService.findQueuedRunsByState(JobRunQueueState.RUNNING))
                .thenReturn(List.of(queuedRun));
        when(jobDataService.findJobRunById(42)).thenReturn(Optional.of(jobRun(JobRunState.BUSY)));

        service.reconcileRunningQueuedRuns();

        verify(jobDataService, never()).deleteQueuedRunById(anyInt());
        verify(jobService, never()).updateJobRunState(any(), any(), any());
    }

    @Test
    void reconcileAbandonsARunningRowWhoseHeartbeatHasGoneStale() {
        stubHeartbeatTimeout(1, DEFAULT_HEARTBEAT_TIMEOUT);
        final var staleHeartbeat = Instant.now().minus(DEFAULT_HEARTBEAT_TIMEOUT).minusSeconds(5);
        final var queuedRun = queuedRun(1, 42, staleHeartbeat);
        final var jobRun = jobRun(JobRunState.BUSY);
        when(jobDataService.findQueuedRunsByState(JobRunQueueState.RUNNING))
                .thenReturn(List.of(queuedRun));
        when(jobDataService.findJobRunById(42)).thenReturn(Optional.of(jobRun));

        service.reconcileRunningQueuedRuns();

        // Reason unknown from here (could be a dead runner, a network partition, or just a very
        // slow job) - UNFINISHED, not ERROR/CANCELLED which imply a known cause.
        verify(jobService).updateJobRunState(eq(jobRun), eq(JobRunState.UNFINISHED), any());
        verify(jobDataService).deleteQueuedRunById(1);
        verify(heartbeatRegistry).stop(42);
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

    @SuppressWarnings("unchecked")
    private void stubHeartbeatTimeout(final int jobId, final Duration timeout) {
        final var job = new TestJob();
        final var property =
                (JobProperty<Object>) (JobProperty<?>) HEARTBEAT_TIMEOUT.createProperty(timeout);
        when(jobService.getAllJobs()).thenReturn(List.of(job));
        when(jobService.findJobId(TestJob.class)).thenReturn(Optional.of(jobId));
        when(jobService.getProperties(job)).thenReturn(new JobProperties(List.of(property)));
    }

    private static JobQueuedRun queuedRun(final int id, final Integer jobRunId) {
        return queuedRun(id, jobRunId, Instant.now());
    }

    private static JobQueuedRun queuedRun(
            final int id, final Integer jobRunId, final Instant lastHeartbeat) {
        final var now = Instant.now();
        return new JobQueuedRun(
                id,
                null,
                1,
                "1.0.0",
                JobRunQueueState.RUNNING,
                jobRunId,
                "runner",
                lastHeartbeat,
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
