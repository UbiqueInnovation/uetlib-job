package ch.uetlib.job.service.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.exception.ManualTriggerNotAllowedException;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobDaemon;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.client.JobInstanceInfo;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.properties.JobPropertyDefinitions;
import ch.uetlib.job.model.properties.enums.JobManualTriggerMode;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.model.queue.QueueNewRunResult;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.executor.JobManualTriggerService;
import ch.uetlib.job.service.info.JobRunnerInfoService;
import ch.uetlib.job.service.inputs.JobRunInputManager;
import ch.uetlib.job.service.instance.JobDaemonRegistrationService;
import ch.uetlib.job.service.logs.LogService;
import ch.uetlib.job.service.triggers.CronTriggerService;
import ch.uetlib.job.service.triggers.TriggerCoordinatorService;
import ch.uetlib.job.testjob.TestJob;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The queue, manual-trigger, job-info and run-action methods behind the status interface. */
class JobControllerServiceTest {

    private static final JobClassAndId JOB = new JobClassAndId(1, "ch.uetlib.job.TestJob");

    private final JobDataService jobDataService = mock(JobDataService.class);
    private final JobService jobService = mock(JobService.class);
    private final TriggerCoordinatorService triggerCoordinatorService =
            mock(TriggerCoordinatorService.class);
    private final JobRunInputManager jobRunInputManager = mock(JobRunInputManager.class);
    private final JobDaemonRegistrationService jobDaemonRegistrationService =
            mock(JobDaemonRegistrationService.class);

    private final JobControllerService service =
            new JobControllerService(
                    jobDataService,
                    jobService,
                    mock(CronTriggerService.class),
                    triggerCoordinatorService,
                    mock(JobManualTriggerService.class),
                    jobRunInputManager,
                    mock(LogService.class),
                    mock(JobRunnerInfoService.class),
                    jobDaemonRegistrationService);

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
    void updateQueuedRunPriorityWithoutAPriorityResetsItToTheJobsPriority() {
        when(jobDataService.findQueuedRunsByIds(List.of(5))).thenReturn(List.of(queuedRun(5)));
        when(jobService.findJobById(JOB.id())).thenReturn(Optional.of(JOB));
        when(jobService.getProperties(JOB))
                .thenReturn(
                        new JobProperties(
                                List.of(
                                        castToObjectProperty(
                                                JobPropertyDefinitions.PRIORITY.createProperty(
                                                        7)))));
        when(jobDataService.updateQueuedRunPriority(5, 7)).thenReturn(true);

        assertTrue(service.updateQueuedRunPriority(5, null).success());
    }

    @Test
    void runQueuedRunNowMarksTheRowWhenItsJobIsEnabledAndAllowsManualRuns() {
        stubRunNowChecks(true, new JobProperties(List.of()));
        when(jobDataService.setAllowQueuedRunToRunNow(5)).thenReturn(true);

        assertTrue(service.runQueuedRunNow(5).success());
        verify(jobDataService).setAllowQueuedRunToRunNow(5);
    }

    @Test
    void runQueuedRunNowReportsFailureWhenTheRowIsNotFound() {
        when(jobDataService.findQueuedRunsByIds(List.of(5))).thenReturn(List.of());

        assertFalse(service.runQueuedRunNow(5).success());
        verify(jobDataService, never()).setAllowQueuedRunToRunNow(anyInt());
    }

    @Test
    void runQueuedRunNowReportsFailureWhenTheJobIsDisabled() {
        stubRunNowChecks(false, new JobProperties(List.of()));

        assertFalse(service.runQueuedRunNow(5).success());
        verify(jobDataService, never()).setAllowQueuedRunToRunNow(anyInt());
    }

    @Test
    void runQueuedRunNowReportsFailureWhenTheJobDisallowsNewManualRuns() {
        stubRunNowChecks(
                true,
                new JobProperties(
                        List.of(
                                castToObjectProperty(
                                        JobPropertyDefinitions.JOB_MANUAL_TRIGGER_MODE
                                                .createProperty(
                                                        JobManualTriggerMode
                                                                .REPLAY_RUN_ALLOWED)))));

        assertFalse(service.runQueuedRunNow(5).success());
        verify(jobDataService, never()).setAllowQueuedRunToRunNow(anyInt());
    }

    @Test
    void runQueuedRunNowReportsFailureWhenTheRowCanNoLongerBeMarked() {
        // Claimed, running or a continuation by the time of the conditional update.
        stubRunNowChecks(true, new JobProperties(List.of()));
        when(jobDataService.setAllowQueuedRunToRunNow(5)).thenReturn(false);

        assertFalse(service.runQueuedRunNow(5).success());
    }

    private void stubRunNowChecks(final boolean enabled, final JobProperties properties) {
        when(jobDataService.findQueuedRunsByIds(List.of(5))).thenReturn(List.of(queuedRun(5)));
        when(jobService.findJobById(JOB.id())).thenReturn(Optional.of(JOB));
        when(jobService.getJobClassOrThrow(JOB)).thenAnswer(invocation -> TestJob.class);
        when(jobService.isEnabled(TestJob.class.getCanonicalName())).thenReturn(enabled);
        when(jobService.instantiateJob(TestJob.class)).thenReturn(new TestJob());
        when(jobService.getProperties(any(TestJob.class))).thenReturn(properties);
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
        final var queued = new QueueNewRunResult(queuedRun(1), true);
        when(jobService.tryQueueNewRun(eq(JOB), any())).thenReturn(queued);

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
        verify(jobService, never()).tryQueueNewRun(any(), any());
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
        verify(jobService, never()).tryQueueNewRun(any(), any());
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

    @Test
    void getInstancesListsThePrimaryFirstThenTheNewest() {
        final var now = Instant.now();
        when(jobDaemonRegistrationService.liveDaemons())
                .thenReturn(
                        List.of(
                                instance("older", false, now.minusSeconds(600)),
                                instance("primary", true, now.minusSeconds(1200)),
                                instance("newer", false, now.minusSeconds(60))));

        final var instances = service.getInstances().instances();

        assertEquals(
                List.of("primary", "newer", "older"),
                instances.stream().map(JobInstanceInfo::name).toList());
        assertTrue(instances.getFirst().primary());
        assertEquals(List.of("a", "b"), instances.getFirst().taints());
    }

    private static JobDaemon instance(
            final String name, final boolean primary, final Instant startedAt) {
        return new JobDaemon(
                1,
                name,
                "1.0.0",
                Set.of("b", "a"),
                true,
                2,
                primary,
                primary ? startedAt : null,
                startedAt,
                Instant.now());
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
                now,
                now,
                Set.of(),
                new JobRunArguments(),
                false);
    }
}
