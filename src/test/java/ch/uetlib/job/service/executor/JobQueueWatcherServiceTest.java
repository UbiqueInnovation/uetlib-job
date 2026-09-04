package ch.uetlib.job.service.executor;

import static ch.uetlib.job.model.properties.JobPropertyDefinitions.CANCELLED_QUEUE_RETENTION;
import static ch.uetlib.job.model.properties.JobPropertyDefinitions.CLAIM_TIMEOUT;
import static ch.uetlib.job.model.properties.JobPropertyDefinitions.QUEUE_TIMEOUT;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.service.JobService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
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
    private static final Duration DEFAULT_QUEUE_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration DEFAULT_CANCELLED_QUEUE_RETENTION = Duration.ofDays(7);

    private final JobService jobService = mock(JobService.class);
    private final JobDataService jobDataService = mock(JobDataService.class);

    private final JobQueueWatcherService service =
            new JobQueueWatcherService(jobService, jobDataService);

    @Test
    void resetAbandonedClaimsUsesDefaultTimeoutWhenNoPropertyOverride() {
        when(jobService.propertyValueByJobId(CLAIM_TIMEOUT))
                .thenReturn(Map.of(7, DEFAULT_CLAIM_TIMEOUT));
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
        when(jobService.propertyValueByJobId(QUEUE_TIMEOUT))
                .thenReturn(Map.of(7, DEFAULT_QUEUE_TIMEOUT));
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
        when(jobService.propertyValueByJobId(CANCELLED_QUEUE_RETENTION))
                .thenReturn(Map.of(7, DEFAULT_CANCELLED_QUEUE_RETENTION));
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
    void deleteOldCancelledQueuedRunsAlsoSweepsRowsOfJobsThisBuildDoesNotKnow() {
        // The per-job pass is keyed by known jobs, so without this second pass a row cancelled
        // because its job is unknown is never deleted at all.
        when(jobService.propertyValueByJobId(CANCELLED_QUEUE_RETENTION))
                .thenReturn(Map.of(7, DEFAULT_CANCELLED_QUEUE_RETENTION));

        service.deleteOldCancelledQueuedRuns();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Collection<Integer>> knownJobIds =
                ArgumentCaptor.forClass(Collection.class);
        final var retainAfter = ArgumentCaptor.forClass(Instant.class);
        verify(jobDataService)
                .deleteOldCancelledQueuedRunsForUnknownJobs(
                        knownJobIds.capture(), retainAfter.capture());
        assertEquals(Set.of(7), Set.copyOf(knownJobIds.getValue()));
        final var expectedRetention = Instant.now().minus(DEFAULT_CANCELLED_QUEUE_RETENTION);
        assertTrue(
                Duration.between(retainAfter.getValue(), expectedRetention)
                                .abs()
                                .compareTo(Duration.ofSeconds(5))
                        < 0);
    }

    @Test
    void reconcileDeletesTheQueuedRowOnceItsLinkedJobRunIsNoLongerRunning() {
        final var queuedRun = queuedRun(1, 42);
        when(jobDataService.findQueuedRunsByState(JobRunQueueState.RUNNING))
                .thenReturn(List.of(queuedRun));
        when(jobDataService.findJobRunById(42)).thenReturn(Optional.of(jobRun(JobRunState.DONE)));

        service.reconcileRunningQueuedRuns();

        verify(jobDataService).deleteQueuedRunById(1);
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
    void reconcileLeavesARunningRowAloneWhileItsLinkedJobRunIsStillRunning() {
        final var queuedRun = queuedRun(1, 42);
        when(jobDataService.findQueuedRunsByState(JobRunQueueState.RUNNING))
                .thenReturn(List.of(queuedRun));
        when(jobDataService.findJobRunById(42)).thenReturn(Optional.of(jobRun(JobRunState.BUSY)));

        service.reconcileRunningQueuedRuns();

        verify(jobDataService, never()).deleteQueuedRunById(anyInt());
        verify(jobService, never()).updateJobRunState(any(), any(), any());
    }

    @Test
    void reconcileDefensivelyResetsARunningRowWithNoLinkedJobRunId() {
        final var queuedRun = queuedRun(1, null);
        when(jobDataService.findQueuedRunsByState(JobRunQueueState.RUNNING))
                .thenReturn(List.of(queuedRun));

        service.reconcileRunningQueuedRuns();

        verify(jobDataService).resetQueuedRunById(1, null);
        verify(jobDataService).findJobRunsByIds(List.of());
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
                Set.of(),
                new JobRunArguments());
    }

    private static JobRun jobRun(final JobRunState state) {
        return new JobRun(
                42, 1, "1.0.0", Instant.now(), null, state, JobRunnerKey.CURRENT, null, null);
    }
}
