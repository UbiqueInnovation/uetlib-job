package ch.uetlib.job.service.executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.executer.JobSubmissionStatus;
import ch.uetlib.job.model.executer.SuccessfulSubmissionResult;
import ch.uetlib.job.model.executer.UnsuccessfulSubmissionResult;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.properties.JobProperty;
import ch.uetlib.job.model.properties.JobPropertyDefinitions;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.executor.runners.JobRunner;
import ch.uetlib.job.service.instance.JobInstanceIdentity;
import ch.uetlib.job.testjob.TestJob;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Both entry points: runner routing, disabled jobs, and recording the queue row before the run. */
class JobExecutorServiceTest {

    private static final JobClassAndId JOB = new JobClassAndId(1, TestJob.class.getCanonicalName());
    private static final String INSTANCE_NAME = "test-instance";
    private static final int JOB_RUN_ID = 99;

    private final JobService jobService = mock(JobService.class);
    private final JobRunner currentRunner = mock(JobRunner.class);
    private final JobRunner dedicatedRunner = mock(JobRunner.class);

    // JobExecutorService's constructor reads JobRunner#key() immediately to build its lookup map,
    // so these need to be stubbed before the service field below is initialized - an @BeforeEach
    // method runs too late, after all field initializers (including service's) have already run.
    {
        when(currentRunner.key()).thenReturn(JobRunnerKey.CURRENT);
        when(dedicatedRunner.key()).thenReturn(JobRunnerKey.DEDICATED_K8S_JOB);
    }

    private final JobExecutorService service =
            new JobExecutorService(
                    jobService,
                    new JobInstanceIdentity(INSTANCE_NAME, Set.of(), Instant.now()),
                    List.of(currentRunner, dedicatedRunner));

    @Test
    void routesToTheCurrentRunnerWhenNotDedicated() {
        stubHappyPath();
        when(currentRunner.submit(any(), any())).thenReturn(true);

        final var result = service.submit(queuedRun(), false);

        final var success = assertInstanceOf(SuccessfulSubmissionResult.class, result);
        assertEquals(JobRunnerKey.CURRENT, success.runnerKey());
        verify(currentRunner).submit(any(), any());
        verify(dedicatedRunner, never()).submit(any(), any());
    }

    @Test
    void routesToTheDedicatedRunnerWhenDedicated() {
        stubHappyPath();
        when(dedicatedRunner.submit(any(), any())).thenReturn(true);

        final var result = service.submit(queuedRun(), true);

        final var success = assertInstanceOf(SuccessfulSubmissionResult.class, result);
        assertEquals(JobRunnerKey.DEDICATED_K8S_JOB, success.runnerKey());
        verify(dedicatedRunner).submit(any(), any());
        verify(currentRunner, never()).submit(any(), any());
    }

    @Test
    void aClaimedRowIsLinkedToItsRunBeforeTheRunnerIsHandedTheJob() {
        stubHappyPath();
        when(jobService.setRunId(any(), anyInt())).thenReturn(true);
        when(currentRunner.submit(any(), any())).thenReturn(true);
        final var row = queuedRun();

        service.submit(row, false);

        final var inOrder = inOrder(jobService, currentRunner);
        inOrder.verify(jobService).setRunId(row, JOB_RUN_ID);
        inOrder.verify(currentRunner).submit(any(), any());
    }

    @Test
    void aDedicatedRunRecordsItsRunningRowBeforeTheRunnerIsHandedTheJob() {
        // A BaseStateJob handoff on the runner's thread re-queues this very row, so it must exist
        // before the job starts - recording it afterwards could insert a second row for the run.
        stubHappyPath();
        when(dedicatedRunner.submit(any(), any())).thenReturn(true);

        final var result =
                service.submitDedicatedJobRun(TestJob.class, new JobRunArguments(), JOB_RUN_ID);

        final var success = assertInstanceOf(SuccessfulSubmissionResult.class, result);
        assertEquals(JobRunnerKey.DEDICATED_K8S_JOB, success.runnerKey());
        final var inOrder = inOrder(jobService, dedicatedRunner);
        inOrder.verify(jobService)
                .queueRunningRun(eq(JOB), any(), eq(JOB_RUN_ID), eq(INSTANCE_NAME));
        inOrder.verify(dedicatedRunner).submit(any(), any());
        // Not a claim: a dispatched run's row is already RUNNING, so linking it would only fail.
        verify(jobService, never()).setRunId(any(), anyInt());
    }

    @Test
    void aDedicatedRunWhoseRowCannotBeRecordedIsNotStarted() {
        stubHappyPath();
        when(jobService.queueRunningRun(any(), any(), anyInt(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        final var result =
                service.submitDedicatedJobRun(TestJob.class, new JobRunArguments(), JOB_RUN_ID);

        final var unsuccessful = assertInstanceOf(UnsuccessfulSubmissionResult.class, result);
        assertEquals(JobSubmissionStatus.FAILED, unsuccessful.status());
        verify(dedicatedRunner, never()).submit(any(), any());
        verify(jobService).updateJobRunState(any(), eq(JobRunState.ERROR), any());
    }

    @Test
    void aDisabledJobIsDisallowedWithoutTouchingAnyRunner() {
        when(jobService.findJobById(JOB.id())).thenReturn(Optional.of(JOB));
        doReturn(TestJob.class).when(jobService).getJobClassOrThrow(JOB);
        when(jobService.isEnabled(TestJob.class.getCanonicalName())).thenReturn(false);

        final var result = service.submit(queuedRun(), false);

        final var unsuccessful = assertInstanceOf(UnsuccessfulSubmissionResult.class, result);
        assertEquals(JobSubmissionStatus.DISALLOWED, unsuccessful.status());
        verify(currentRunner, never()).submit(any(), any());
        verify(dedicatedRunner, never()).submit(any(), any());
    }

    @Test
    void aJobAlreadyRunningIsDisallowedByDefault() {
        when(jobService.findJobById(JOB.id())).thenReturn(Optional.of(JOB));
        doReturn(TestJob.class).when(jobService).getJobClassOrThrow(JOB);
        when(jobService.isEnabled(TestJob.class.getCanonicalName())).thenReturn(true);
        when(jobService.instantiateJob(TestJob.class)).thenReturn(new TestJob());
        when(jobService.getProperties(any(TestJob.class))).thenReturn(new JobProperties(List.of()));
        when(jobService.findJobId(TestJob.class)).thenReturn(Optional.of(JOB.id()));
        when(jobService.getLastJobRun(JOB.id())).thenReturn(Optional.of(runningLastRun()));

        final var result = service.submit(queuedRun(), false);

        final var unsuccessful = assertInstanceOf(UnsuccessfulSubmissionResult.class, result);
        assertEquals(JobSubmissionStatus.DISALLOWED, unsuccessful.status());
        verify(currentRunner, never()).submit(any(), any());
        verify(dedicatedRunner, never()).submit(any(), any());
    }

    @Test
    void aJobAlreadyRunningIsAllowedWhenTheJobPermitsParallelRuns() {
        when(jobService.findJobById(JOB.id())).thenReturn(Optional.of(JOB));
        doReturn(TestJob.class).when(jobService).getJobClassOrThrow(JOB);
        when(jobService.isEnabled(TestJob.class.getCanonicalName())).thenReturn(true);
        when(jobService.instantiateJob(TestJob.class)).thenReturn(new TestJob());
        when(jobService.getProperties(any(TestJob.class)))
                .thenReturn(jobPropertiesWith(JobPropertyDefinitions.ALLOW_PARALLEL_RUNS, true));
        when(jobService.jobClassAndIdOrThrow(TestJob.class)).thenReturn(JOB);
        when(jobService.getOrCreateJobRun(any(), any(), any(), any())).thenReturn(jobRun());
        when(jobService.findJobId(TestJob.class)).thenReturn(Optional.of(JOB.id()));
        when(jobService.getLastJobRun(JOB.id())).thenReturn(Optional.of(runningLastRun()));
        when(currentRunner.submit(any(), any())).thenReturn(true);

        final var result = service.submit(queuedRun(), false);

        assertInstanceOf(SuccessfulSubmissionResult.class, result);
        verify(currentRunner).submit(any(), any());
    }

    @SuppressWarnings("unchecked")
    private static <T> JobProperties jobPropertiesWith(
            final ch.uetlib.job.model.properties.JobPropertyDefinition<T> definition,
            final T value) {
        return new JobProperties(
                List.of((JobProperty<Object>) (JobProperty<?>) definition.createProperty(value)));
    }

    @Test
    void aForceRunIsAllowedWhileTheJobIsRunning() {
        when(jobService.findJobById(JOB.id())).thenReturn(Optional.of(JOB));
        doReturn(TestJob.class).when(jobService).getJobClassOrThrow(JOB);
        when(jobService.isEnabled(TestJob.class.getCanonicalName())).thenReturn(true);
        when(jobService.instantiateJob(TestJob.class)).thenReturn(new TestJob());
        when(jobService.getProperties(any(TestJob.class))).thenReturn(new JobProperties(List.of()));
        when(jobService.jobClassAndIdOrThrow(TestJob.class)).thenReturn(JOB);
        when(jobService.getOrCreateJobRun(any(), any(), any(), any())).thenReturn(jobRun());
        when(jobService.findJobId(TestJob.class)).thenReturn(Optional.of(JOB.id()));
        when(jobService.getLastJobRun(JOB.id())).thenReturn(Optional.of(runningLastRun()));
        when(currentRunner.submit(any(), any())).thenReturn(true);

        final var result = service.submit(queuedRun(true), false);

        assertInstanceOf(SuccessfulSubmissionResult.class, result);
        verify(currentRunner).submit(any(), any());
    }

    @Test
    void aRunThatFailsToScheduleIsUnsuccessfulAndMarksTheJobRunAsError() {
        stubHappyPath();
        when(currentRunner.submit(any(), any())).thenReturn(false);

        final var result = service.submit(queuedRun(), false);

        final var unsuccessful = assertInstanceOf(UnsuccessfulSubmissionResult.class, result);
        assertEquals(JobSubmissionStatus.FAILED, unsuccessful.status());
        verify(jobService).updateJobRunState(any(), eq(JobRunState.ERROR), any());
    }

    @Test
    void aQueuedRunCanStartWhenItsJobIsEnabledAndNotRunning() {
        stubStartChecks(new JobProperties(List.of()), Optional.empty());

        assertTrue(service.canStart(waitingRun(false)));
    }

    @Test
    void aQueuedRunOfADisabledJobCannotStart() {
        when(jobService.findJobById(JOB.id())).thenReturn(Optional.of(JOB));
        doReturn(TestJob.class).when(jobService).getJobClassOrThrow(JOB);
        when(jobService.isEnabled(TestJob.class.getCanonicalName())).thenReturn(false);

        assertFalse(service.canStart(waitingRun(false)));
    }

    @Test
    void aQueuedRunCannotStartWhileItsJobIsRunningAndDisallowsParallelRuns() {
        stubStartChecks(new JobProperties(List.of()), Optional.of(runningLastRun()));

        assertFalse(service.canStart(waitingRun(false)));
    }

    @Test
    void aQueuedRunCanStartWhileItsJobIsRunningWhenItAllowsParallelRuns() {
        stubStartChecks(
                jobPropertiesWith(JobPropertyDefinitions.ALLOW_PARALLEL_RUNS, true),
                Optional.of(runningLastRun()));

        assertTrue(service.canStart(waitingRun(false)));
    }

    @Test
    void aForcedQueuedRunCanStartWhileItsJobIsRunning() {
        // FORCE_RUN_OVERRIDES_ALLOW_PARALLEL_RUNS defaults to true.
        stubStartChecks(new JobProperties(List.of()), Optional.of(runningLastRun()));

        assertTrue(service.canStart(waitingRun(true)));
    }

    @Test
    void aHandoffContinuationCanStartAlthoughItsOwnRunCountsAsRunning() {
        // A BaseStateJob run waiting in SCHEDULED for its next runner is this very run, not
        // another.
        stubStartChecks(new JobProperties(List.of()), Optional.of(runningLastRun()));

        assertTrue(service.canStart(continuationOf(runningLastRun().id())));
    }

    private void stubStartChecks(final JobProperties properties, final Optional<JobRun> lastRun) {
        when(jobService.findJobById(JOB.id())).thenReturn(Optional.of(JOB));
        doReturn(TestJob.class).when(jobService).getJobClassOrThrow(JOB);
        when(jobService.isEnabled(TestJob.class.getCanonicalName())).thenReturn(true);
        when(jobService.instantiateJob(TestJob.class)).thenReturn(new TestJob());
        when(jobService.getProperties(any(TestJob.class))).thenReturn(properties);
        when(jobService.findJobId(TestJob.class)).thenReturn(Optional.of(JOB.id()));
        when(jobService.getLastJobRun(JOB.id())).thenReturn(lastRun);
    }

    /** A still-{@code QUEUED} row, as the dispatcher asks about it before claiming. */
    private static JobQueuedRun waitingRun(final boolean forceRun) {
        final var now = Instant.now();
        return new JobQueuedRun(
                1,
                null,
                JOB.id(),
                "1.0.0",
                JobRunQueueState.QUEUED,
                null,
                null,
                now,
                now,
                Set.of(),
                new JobRunArguments(forceRun));
    }

    /** A {@code BaseStateJob} handoff row: still {@code QUEUED}, but bound to its run. */
    private static JobQueuedRun continuationOf(final int jobRunId) {
        final var now = Instant.now();
        return new JobQueuedRun(
                1,
                null,
                JOB.id(),
                "1.0.0",
                JobRunQueueState.QUEUED,
                jobRunId,
                null,
                now,
                now,
                Set.of(),
                new JobRunArguments());
    }

    private void stubHappyPath() {
        when(jobService.findJobById(JOB.id())).thenReturn(Optional.of(JOB));
        doReturn(TestJob.class).when(jobService).getJobClassOrThrow(JOB);
        when(jobService.isEnabled(TestJob.class.getCanonicalName())).thenReturn(true);
        when(jobService.instantiateJob(TestJob.class)).thenReturn(new TestJob());
        when(jobService.getProperties(any(TestJob.class))).thenReturn(new JobProperties(List.of()));
        when(jobService.jobClassAndIdOrThrow(TestJob.class)).thenReturn(JOB);
        when(jobService.getOrCreateJobRun(any(), any(), any(), any())).thenReturn(jobRun());
    }

    private static JobQueuedRun queuedRun() {
        return queuedRun(false);
    }

    private static JobQueuedRun queuedRun(final boolean forceRun) {
        final var now = Instant.now();
        return new JobQueuedRun(
                1,
                null,
                JOB.id(),
                "1.0.0",
                JobRunQueueState.CLAIMED,
                null,
                INSTANCE_NAME,
                now,
                now,
                Set.of(),
                new JobRunArguments(forceRun));
    }

    private static JobRun jobRun() {
        return new JobRun(
                JOB_RUN_ID,
                JOB.id(),
                "1.0.0",
                Instant.now(),
                null,
                JobRunState.SCHEDULED,
                JobRunnerKey.CURRENT,
                null,
                null);
    }

    /**
     * A distinct job run - not this submission's own - whose state is still {@code isRunning()}.
     */
    private static JobRun runningLastRun() {
        return new JobRun(
                50,
                JOB.id(),
                "1.0.0",
                Instant.now(),
                null,
                JobRunState.BUSY,
                JobRunnerKey.CURRENT,
                null,
                null);
    }
}
