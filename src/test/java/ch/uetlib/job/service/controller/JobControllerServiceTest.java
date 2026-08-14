package ch.uetlib.job.service.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.executor.JobManualTriggerService;
import ch.uetlib.job.service.info.JobRunnerInfoService;
import ch.uetlib.job.service.inputs.JobRunInputManager;
import ch.uetlib.job.service.logs.LogService;
import ch.uetlib.job.service.triggers.CronTriggerService;
import ch.uetlib.job.service.triggers.TriggerCoordinatorService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Covers the queue-facing half of {@link JobControllerService} - {@code getQueueInfo}, {@code
 * deleteQueuedRun(s)}, and {@code updateQueuedRunPriority} - which backs the status interface's
 * queue view/actions and had no dedicated coverage before.
 */
class JobControllerServiceTest {

    private static final JobClassAndId JOB = new JobClassAndId(1, "ch.uetlib.job.TestJob");

    private final JobDataService jobDataService = mock(JobDataService.class);
    private final JobService jobService = mock(JobService.class);

    private final JobControllerService service =
            new JobControllerService(
                    jobDataService,
                    jobService,
                    mock(CronTriggerService.class),
                    mock(TriggerCoordinatorService.class),
                    mock(JobManualTriggerService.class),
                    mock(JobRunInputManager.class),
                    mock(LogService.class),
                    mock(JobRunnerInfoService.class));

    @Test
    void getQueueInfoMapsThePageAndTheOldestAndNewestEntries() {
        when(jobService.getAllJobClassesWithId()).thenReturn(List.of(JOB));
        when(jobService.findJobById(JOB.id())).thenReturn(Optional.of(JOB));
        final var run = queuedRun(1);
        when(jobDataService.findQueuedRuns(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(run), PageRequest.of(0, 10), 1));
        when(jobDataService.findOldestQueuedRun()).thenReturn(Optional.of(run));
        when(jobDataService.findNewestQueuedRun()).thenReturn(Optional.of(run));

        final var result =
                service.getQueueInfo(
                        PageRequest.of(0, 10), List.of(), List.of(), List.of(), null, null);

        assertEquals(1, result.elements().size());
        assertEquals(run.jobQueuedRunId(), result.elements().get(0).id());
        assertEquals(1, result.length());
        assertNotNull(result.oldest());
        assertNotNull(result.newest());
        // Not RUNNING - a queued-but-unclaimed row has no heartbeat to judge, so no verdict either.
        assertNull(result.elements().get(0).heartbeatStale());
    }

    @Test
    void getQueueInfoMarksARunningRowWithAStaleHeartbeatAsStale() {
        when(jobService.getAllJobClassesWithId()).thenReturn(List.of(JOB));
        when(jobService.findJobById(JOB.id())).thenReturn(Optional.of(JOB));
        when(jobService.getProperties(JOB)).thenReturn(new JobProperties(List.of()));
        final var run = runningQueuedRun(1, Instant.now().minus(Duration.ofHours(1)));
        when(jobDataService.findQueuedRuns(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(run), PageRequest.of(0, 10), 1));
        when(jobDataService.findOldestQueuedRun()).thenReturn(Optional.empty());
        when(jobDataService.findNewestQueuedRun()).thenReturn(Optional.empty());

        final var result =
                service.getQueueInfo(
                        PageRequest.of(0, 10), List.of(), List.of(), List.of(), null, null);

        assertEquals(Boolean.TRUE, result.elements().get(0).heartbeatStale());
    }

    @Test
    void getQueueInfoMarksARunningRowWithAFreshHeartbeatAsNotStale() {
        when(jobService.getAllJobClassesWithId()).thenReturn(List.of(JOB));
        when(jobService.findJobById(JOB.id())).thenReturn(Optional.of(JOB));
        when(jobService.getProperties(JOB)).thenReturn(new JobProperties(List.of()));
        final var run = runningQueuedRun(1, Instant.now());
        when(jobDataService.findQueuedRuns(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(run), PageRequest.of(0, 10), 1));
        when(jobDataService.findOldestQueuedRun()).thenReturn(Optional.empty());
        when(jobDataService.findNewestQueuedRun()).thenReturn(Optional.empty());

        final var result =
                service.getQueueInfo(
                        PageRequest.of(0, 10), List.of(), List.of(), List.of(), null, null);

        assertEquals(Boolean.FALSE, result.elements().get(0).heartbeatStale());
    }

    @Test
    void deleteQueuedRunReportsSuccessWhenARowWasRemoved() {
        when(jobDataService.deleteQueuedRunIfQueued(5)).thenReturn(true);

        assertTrue(service.deleteQueuedRun(5).success());
    }

    @Test
    void deleteQueuedRunReportsFailureWhenNothingWasRemoved() {
        when(jobDataService.deleteQueuedRunIfQueued(5)).thenReturn(false);

        assertFalse(service.deleteQueuedRun(5).success());
    }

    @Test
    void acknowledgeQueuedRunReportsSuccessWhenACancelledRowWasRemoved() {
        when(jobDataService.acknowledgeQueuedRun(5)).thenReturn(true);

        assertTrue(service.acknowledgeQueuedRun(5).success());
    }

    @Test
    void acknowledgeQueuedRunReportsFailureWhenNothingWasCancelled() {
        when(jobDataService.acknowledgeQueuedRun(5)).thenReturn(false);

        assertFalse(service.acknowledgeQueuedRun(5).success());
    }

    @Test
    void acknowledgeAllCancelledQueuedRunsReportsHowManyWereRemoved() {
        when(jobDataService.acknowledgeAllCancelledQueuedRuns()).thenReturn(3L);

        final var result = service.acknowledgeAllCancelledQueuedRuns();

        assertTrue(result.success());
        assertTrue(result.message().contains("3"));
    }

    @Test
    void deleteQueuedRunsDefaultsToQueuedOnlyWhenNoStatesGiven() {
        when(jobService.getAllJobClassesWithId()).thenReturn(List.of(JOB));
        when(jobDataService.deleteQueuedRuns(any(), any())).thenReturn(3L);

        final var result = service.deleteQueuedRuns(List.of(), List.of(), List.of());

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Collection<JobRunQueueState>> statesCaptor =
                ArgumentCaptor.forClass(Collection.class);
        verify(jobDataService).deleteQueuedRuns(any(), statesCaptor.capture());
        assertEquals(Set.of(JobRunQueueState.QUEUED), Set.copyOf(statesCaptor.getValue()));
        assertTrue(result.success());
    }

    @Test
    void deleteQueuedRunsNeverRemovesRunningEvenIfExplicitlyRequested() {
        when(jobService.getAllJobClassesWithId()).thenReturn(List.of(JOB));

        final var result =
                service.deleteQueuedRuns(List.of(), List.of(), List.of(JobRunQueueState.RUNNING));

        assertFalse(result.success());
        verify(jobDataService, never()).deleteQueuedRuns(any(), any());
    }

    @Test
    void deleteQueuedRunsNeverRemovesClaimedEvenIfExplicitlyRequested() {
        when(jobService.getAllJobClassesWithId()).thenReturn(List.of(JOB));

        final var result =
                service.deleteQueuedRuns(List.of(), List.of(), List.of(JobRunQueueState.CLAIMED));

        assertFalse(result.success());
        verify(jobDataService, never()).deleteQueuedRuns(any(), any());
    }

    @Test
    void updateQueuedRunPriorityReportsSuccessWhenARowWasUpdated() {
        when(jobDataService.updateQueuedRunPriority(5, 10)).thenReturn(true);

        assertTrue(service.updateQueuedRunPriority(5, 10).success());
    }

    @Test
    void updateQueuedRunPriorityReportsFailureWhenNoRowMatched() {
        when(jobDataService.updateQueuedRunPriority(5, 10)).thenReturn(false);

        assertFalse(service.updateQueuedRunPriority(5, 10).success());
    }

    private static JobQueuedRun queuedRun(final int id) {
        final var now = Instant.now();
        return new JobQueuedRun(
                id,
                null,
                JOB.id(),
                "1.0.0",
                JobRunQueueState.QUEUED,
                null,
                null,
                null,
                now,
                now,
                Set.of(),
                new JobRunArguments());
    }

    private static JobQueuedRun runningQueuedRun(final int id, final Instant lastHeartbeat) {
        final var now = Instant.now();
        return new JobQueuedRun(
                id,
                null,
                JOB.id(),
                "1.0.0",
                JobRunQueueState.RUNNING,
                id,
                "runner",
                lastHeartbeat,
                now,
                now,
                Set.of(),
                new JobRunArguments());
    }
}
