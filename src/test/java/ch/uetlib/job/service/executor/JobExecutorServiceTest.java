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
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.executor.runners.JobRunner;
import ch.uetlib.job.testjob.TestJob;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * There is only one live caller of {@link JobExecutorService#submit(JobQueuedRun, boolean)} in the
 * codebase - {@link JobRunQueueService} - which had no dedicated coverage of its own beyond that
 * caller's mocks. This exercises the method directly: routing to the correct {@link JobRunner} by
 * {@code isDedicated}, and the disabled-job short circuit.
 */
class JobExecutorServiceTest {

    private static final JobClassAndId JOB = new JobClassAndId(1, TestJob.class.getCanonicalName());

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
            new JobExecutorService(jobService, List.of(currentRunner, dedicatedRunner));

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
    void aRunThatFailsToScheduleIsUnsuccessfulAndMarksTheJobRunAsError() {
        stubHappyPath();
        when(currentRunner.submit(any(), any())).thenReturn(false);

        final var result = service.submit(queuedRun(), false);

        final var unsuccessful = assertInstanceOf(UnsuccessfulSubmissionResult.class, result);
        assertEquals(JobSubmissionStatus.FAILED, unsuccessful.status());
        verify(jobService).updateJobRunState(any(), eq(JobRunState.ERROR), any());
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
        final var now = Instant.now();
        return new JobQueuedRun(
                1,
                null,
                JOB.id(),
                "1.0.0",
                JobRunQueueState.CLAIMED,
                null,
                "test-instance",
                now,
                now,
                now,
                Set.of(),
                new JobRunArguments());
    }

    private static JobRun jobRun() {
        return new JobRun(
                99,
                JOB.id(),
                "1.0.0",
                Instant.now(),
                null,
                JobRunState.SCHEDULED,
                JobRunnerKey.CURRENT,
                null,
                null);
    }
}
