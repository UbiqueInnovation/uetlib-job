package ch.uetlib.job.service.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.exception.ManualTriggerNotAllowedException;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.properties.JobPropertyDefinitions;
import ch.uetlib.job.model.properties.enums.JobManualTriggerMode;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.executor.JobManualTriggerService;
import ch.uetlib.job.service.info.JobRunnerInfoService;
import ch.uetlib.job.service.inputs.JobRunInputManager;
import ch.uetlib.job.service.logs.LogService;
import ch.uetlib.job.service.triggers.CronTriggerService;
import ch.uetlib.job.service.triggers.TriggerCoordinatorService;
import ch.uetlib.job.testjob.TestJob;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Covers the queue-facing half of {@link JobControllerService} - {@code getQueueInfo}, {@code
 * deleteQueuedRun(s)}, and {@code updateQueuedRunPriority} - as well as the manual-trigger,
 * job-info, and run-action (cancel/acknowledge) methods, which backs the status interface's
 * queue/runs views and actions and had no dedicated coverage before.
 */
class JobControllerServiceTest {

    private static final JobClassAndId JOB = new JobClassAndId(1, "ch.uetlib.job.TestJob");

    private final JobDataService jobDataService = mock(JobDataService.class);
    private final JobService jobService = mock(JobService.class);
    private final TriggerCoordinatorService triggerCoordinatorService =
            mock(TriggerCoordinatorService.class);
    private final JobRunInputManager jobRunInputManager = mock(JobRunInputManager.class);

    private final JobControllerService service =
            new JobControllerService(
                    jobDataService,
                    jobService,
                    mock(CronTriggerService.class),
                    triggerCoordinatorService,
                    mock(JobManualTriggerService.class),
                    jobRunInputManager,
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
        assertEquals(run.jobQueuedRunId(), result.elements().getFirst().id());
        assertEquals(1, result.length());
        assertNotNull(result.oldest());
        assertNotNull(result.newest());
        // Not RUNNING - a queued-but-unclaimed row has no heartbeat to judge, so no verdict either.
        assertNull(result.elements().getFirst().heartbeatStale());
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

    @Test
    void startJobManualQueuesANewRunWhenEnabledAndAllowed()
            throws ManualTriggerNotAllowedException {
        when(jobService.getJobClassOrThrow(TestJob.class.getCanonicalName()))
                .thenAnswer(invocation -> TestJob.class);
        when(jobService.instantiateJob(TestJob.class)).thenReturn(new TestJob());
        when(jobService.getProperties(any(TestJob.class))).thenReturn(new JobProperties(List.of()));
        when(jobService.isEnabled(TestJob.class.getCanonicalName())).thenReturn(true);
        when(jobService.jobClassAndIdOrThrow(TestJob.class)).thenReturn(JOB);
        final var queued = queuedRun(1);
        when(jobService.queueNewRun(eq(JOB), any())).thenReturn(queued);

        final var result = service.startJobManual(TestJob.class.getCanonicalName(), false);

        assertEquals(queued, result);
    }

    @Test
    void startJobManualThrowsWhenTheJobIsDisabled() {
        when(jobService.getJobClassOrThrow(TestJob.class.getCanonicalName()))
                .thenAnswer(invocation -> TestJob.class);
        when(jobService.instantiateJob(TestJob.class)).thenReturn(new TestJob());
        when(jobService.getProperties(any(TestJob.class))).thenReturn(new JobProperties(List.of()));
        when(jobService.isEnabled(TestJob.class.getCanonicalName())).thenReturn(false);

        assertThrows(
                ManualTriggerNotAllowedException.class,
                () -> service.startJobManual(TestJob.class.getCanonicalName(), false));
        verify(jobService, never()).queueNewRun(any(), any());
    }

    @Test
    void startJobManualThrowsWhenManualTriggerModeDisallowsIt() {
        when(jobService.getJobClassOrThrow(TestJob.class.getCanonicalName()))
                .thenAnswer(invocation -> TestJob.class);
        when(jobService.instantiateJob(TestJob.class)).thenReturn(new TestJob());
        when(jobService.getProperties(any(TestJob.class)))
                .thenReturn(
                        new JobProperties(
                                List.of(
                                        castToObjectProperty(
                                                JobPropertyDefinitions.JOB_MANUAL_TRIGGER_MODE
                                                        .createProperty(
                                                                JobManualTriggerMode
                                                                        .MANUAL_TRIGGER_DISALLOWED)))));
        when(jobService.isEnabled(TestJob.class.getCanonicalName())).thenReturn(true);

        assertThrows(
                ManualTriggerNotAllowedException.class,
                () -> service.startJobManual(TestJob.class.getCanonicalName(), false));
        verify(jobService, never()).queueNewRun(any(), any());
    }

    @Test
    void jobInfoReflectsTheJobsConfiguration() {
        when(jobService.getJobClassOrThrow(JOB)).thenAnswer(invocation -> TestJob.class);
        when(jobService.instantiateJob(TestJob.class)).thenReturn(new TestJob());
        when(jobService.getProperties(any(TestJob.class))).thenReturn(new JobProperties(List.of()));
        when(jobService.isEnabled(TestJob.class.getCanonicalName())).thenReturn(true);
        when(triggerCoordinatorService.getTriggers(any(), any())).thenReturn(List.of());

        final var info = service.jobInfo(JOB, Map.of());

        assertEquals(JOB.id(), info.id());
        assertEquals(TestJob.class.getCanonicalName(), info.clazz());
        assertTrue(info.enabled());
        assertFalse(info.propertiesFixed());
        assertFalse(info.triggersFixed());
        assertEquals(0, info.numTriggers());
        assertEquals(JobManualTriggerMode.NEW_RUN_ALLOWED, info.manualTriggerMode());
    }

    @Test
    void cancelJobRunReportsFailureWhenTheRunIsNotFound() {
        when(jobDataService.findJobRunById(7)).thenReturn(Optional.empty());

        final var result = service.cancelJobRun(7);

        assertFalse(result.success());
        verify(jobService, never()).cancel(any());
    }

    @Test
    void cancelJobRunReportsSuccessWhenJobServiceCancelsIt() {
        final var jobRun = jobRun(7, JobRunState.BUSY);
        when(jobDataService.findJobRunById(7)).thenReturn(Optional.of(jobRun));
        when(jobService.cancel(jobRun)).thenReturn(true);

        assertTrue(service.cancelJobRun(7).success());
    }

    @Test
    void cancelJobRunReportsFailureWhenJobServiceCouldNotCancelIt() {
        final var jobRun = jobRun(7, JobRunState.BUSY);
        when(jobDataService.findJobRunById(7)).thenReturn(Optional.of(jobRun));
        when(jobService.cancel(jobRun)).thenReturn(false);

        assertFalse(service.cancelJobRun(7).success());
    }

    @Test
    void acknowledgeJobRunReportsFailureWhenTheStateIsNotAcknowledgeable() {
        when(jobDataService.findJobRunById(7)).thenReturn(Optional.of(jobRun(7, JobRunState.DONE)));

        final var result = service.acknowledgeJobRun(7);

        assertFalse(result.success());
        verify(jobDataService, never()).updateJobRunState(anyInt(), any(), any());
    }

    @Test
    void acknowledgeJobRunUpdatesStateWhenAcknowledgeable() {
        when(jobDataService.findJobRunById(7))
                .thenReturn(Optional.of(jobRun(7, JobRunState.ERROR)));

        final var result = service.acknowledgeJobRun(7);

        assertTrue(result.success());
        verify(jobDataService).updateJobRunState(eq(7), eq(JobRunState.ACKNOWLEDGED), any());
    }

    @Test
    void getAllFilesIsEmptyWhenTheRunHasNoDeclaredInputs() {
        when(jobRunInputManager.getDeclaredInputPaths(7)).thenReturn(List.of());

        assertTrue(service.getAllFiles(7).isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static ch.uetlib.job.model.properties.JobProperty<Object> castToObjectProperty(
            final ch.uetlib.job.model.properties.JobProperty<?> p) {
        return (ch.uetlib.job.model.properties.JobProperty<Object>) p;
    }

    private static JobRun jobRun(final int id, final JobRunState state) {
        final var now = Instant.now();
        return new JobRun(
                id, JOB.id(), "1.0.0", now, null, state, JobRunnerKey.CURRENT, null, null);
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
