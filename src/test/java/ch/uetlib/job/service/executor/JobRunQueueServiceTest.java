package ch.uetlib.job.service.executor;

import static ch.uetlib.job.model.properties.JobPropertyDefinitions.RUNNER;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.properties.JobProperty;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.model.runner.conditions.JobRunnerTaint;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.info.JobRunnerInfoService;
import ch.uetlib.job.testjob.TestJob;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Taint subset-matching used to live in a JSON_TABLE-based SQL query; it is now Java-side logic
 * ({@code JobRunQueueService#matchesTaints}) exercised here via the dedicated-runner match path,
 * which is the simplest one to drive with mocks (it skips the runner-group capacity check the plain
 * match path needs). Also covers the paging strategy and the no-concurrent-execution guard.
 */
class JobRunQueueServiceTest {

    private static final String INSTANCE_NAME = "test-instance";
    private static final String VERSION_TAG = "1.0.0";
    private static final int PAGE_SIZE = 2;

    private final JobService jobService = mock(JobService.class);
    private final JobRunnerInfoService jobRunnerInfoService = mock(JobRunnerInfoService.class);
    private final JobExecutorService jobExecutorService = mock(JobExecutorService.class);

    private final JobRunQueueService service =
            new JobRunQueueService(
                    jobService,
                    jobRunnerInfoService,
                    jobExecutorService,
                    INSTANCE_NAME,
                    List.of("gpu"),
                    PAGE_SIZE);

    {
        // Every queuedRun(...) below is tagged VERSION_TAG; matching this instance's own version is
        // otherwise a precondition for every claim attempt (see claimIfMatchesThisRunner), so stub
        // it once here rather than repeating it in every test.
        when(jobService.versionTag()).thenReturn(VERSION_TAG);
    }

    @Test
    void keepsPagingUntilAPageHasAMatch() {
        final var nonMatching = queuedRun(1, Set.of(JobRunnerTaint.from("unadvertised")));
        final var matching = queuedRun(2, Set.of(JobRunnerTaint.from("gpu")));
        stubDedicatedClaimAttempt();
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(List.of(nonMatching), PageRequest.of(0, PAGE_SIZE), true));
        when(jobService.findQueuedRunsPage(PageRequest.of(1, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(List.of(matching), PageRequest.of(1, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService).findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE));
        verify(jobService).findQueuedRunsPage(PageRequest.of(1, PAGE_SIZE));
        verify(jobService).claimQueuedRun(matching, INSTANCE_NAME);
        verify(jobService, never()).claimQueuedRun(nonMatching, INSTANCE_NAME);
    }

    @Test
    void stopsPagingAsSoonAsAPageHasAMatch() {
        final var matching = queuedRun(1, Set.of(JobRunnerTaint.from("gpu")));
        stubDedicatedClaimAttempt();
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(new SliceImpl<>(List.of(matching), PageRequest.of(0, PAGE_SIZE), true));

        service.checkForRunnableAndSchedule();

        verify(jobService).findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE));
        verify(jobService, never()).findQueuedRunsPage(PageRequest.of(1, PAGE_SIZE));
    }

    @Test
    void stopsAtTheLastPageWhenNothingEverMatches() {
        final var nonMatching = queuedRun(1, Set.of(JobRunnerTaint.from("unadvertised")));
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(List.of(nonMatching), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService).findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE));
        verify(jobService, never()).claimQueuedRun(any(), any());
    }

    @Test
    void aConcurrentInvocationIsSkippedRatherThanRunInParallel() {
        final var run = queuedRun(1, Set.of(JobRunnerTaint.from("gpu")));
        stubDedicatedClaimAttempt();
        // Simulates a second scheduled tick arriving while the first check is still in progress:
        // re-entering from inside the mocked call, on the same thread, hits the exact same
        // AtomicBoolean guard a genuinely concurrent invocation would.
        when(jobService.findQueuedRunsPage(any()))
                .thenAnswer(
                        invocation -> {
                            service.checkForRunnableAndSchedule();
                            return new SliceImpl<>(
                                    List.of(run), PageRequest.of(0, PAGE_SIZE), false);
                        });

        service.checkForRunnableAndSchedule();

        verify(jobService, times(1)).findQueuedRunsPage(any());
    }

    @Test
    void anUntaggedRowIsRoutedToThePlainPathNotDedicated() {
        // An untagged row's taints (the empty set) are trivially a subset of any taint set. Which
        // path applies is decided by the job's own RUNNER property (default CURRENT, not stubbed
        // otherwise here), not by taint-matching order, so this must never reach the dedicated path
        // just because the empty set also happens to satisfy it.
        final var untagged = queuedRun(1, Set.of());
        stubPlainClaimAttempt();
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(List.of(untagged), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService).claimQueuedRun(untagged, INSTANCE_NAME);
        verify(jobService, never()).hasRunner(JobRunnerKey.DEDICATED_K8S_JOB);
    }

    @Test
    void aDedicatedJobWithNoTaintsOfItsOwnStillRoutesToTheDedicatedPath() {
        // Regression test: routing must come from the job's own RUNNER property, not from its
        // taints. A DEDICATED_K8S_JOB with no taints of its own must still be dispatched as
        // dedicated, never fall through to the plain CurrentJobRunner path just because the empty
        // set trivially satisfies the taint check.
        final var untaggedButDedicated = queuedRun(1, Set.of());
        stubDedicatedClaimAttempt();
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(
                                List.of(untaggedButDedicated), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService).hasRunner(JobRunnerKey.DEDICATED_K8S_JOB);
        verify(jobService).claimQueuedRun(untaggedButDedicated, INSTANCE_NAME);
    }

    @Test
    void aPlainJobIsNeverRoutedToDedicatedEvenIfItsTaintsIncludeTheDedicatedMarker() {
        // The inverse of the above: taints alone must not be able to force dedicated routing for a
        // job whose RUNNER is (the default) CURRENT.
        final var taggedButNotDedicated =
                queuedRun(1, Set.of(JobRunnerTaints.DEDICATED_K8S_TAINT.taint()));
        stubJobRunnerKey(JobRunnerKey.CURRENT);
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(
                                List.of(taggedButNotDedicated), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService, never()).hasRunner(any());
        verify(jobService, never()).claimQueuedRun(any(), any());
    }

    @Test
    void aRowEnqueuedByADifferentVersionIsNeverClaimed() {
        // Staggered/rolling release: this instance is version VERSION_TAG, but the row was enqueued
        // by a different version - it must be left for an instance running that exact version,
        // never claimed here even though taints would otherwise match.
        final var otherVersion = queuedRun(1, Set.of(JobRunnerTaint.from("gpu")), "2.0.0");
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(
                                List.of(otherVersion), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService, never()).claimQueuedRun(any(), any());
        verify(jobService, never()).hasRunner(any());
    }

    @Test
    void keepsPagingPastRowsFromAnotherVersionToFindAMatchingOne() {
        final var otherVersion =
                queuedRun(1, Set.of(JobRunnerTaints.DEDICATED_K8S_TAINT.taint()), "2.0.0");
        final var sameVersion = queuedRun(2, Set.of(JobRunnerTaints.DEDICATED_K8S_TAINT.taint()));
        stubDedicatedClaimAttempt();
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(List.of(otherVersion), PageRequest.of(0, PAGE_SIZE), true));
        when(jobService.findQueuedRunsPage(PageRequest.of(1, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(List.of(sameVersion), PageRequest.of(1, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService).claimQueuedRun(sameVersion, INSTANCE_NAME);
        verify(jobService, never()).claimQueuedRun(otherVersion, INSTANCE_NAME);
    }

    private void stubPlainClaimAttempt() {
        when(jobService.findJobById(anyInt()))
                .thenReturn(Optional.of(new JobClassAndId(1, "ch.uetlib.job.TestJob")));
        when(jobService.getProperties(any(JobClassAndId.class)))
                .thenReturn(new JobProperties(List.of()));
        when(jobService.claimQueuedRun(any(), any())).thenReturn(false);
    }

    private void stubDedicatedClaimAttempt() {
        when(jobService.findJobById(anyInt()))
                .thenReturn(Optional.of(new JobClassAndId(1, "ch.uetlib.job.TestJob")));
        when(jobService.hasRunner(JobRunnerKey.DEDICATED_K8S_JOB)).thenReturn(true);
        // Claim "fails" (already claimed elsewhere) so the test doesn't need to also stub a full
        // successful submission through JobExecutorService - only that a claim was attempted.
        when(jobService.claimQueuedRun(any(), any())).thenReturn(false);
        stubJobRunnerKey(JobRunnerKey.DEDICATED_K8S_JOB);
    }

    /**
     * Which claim path even applies is decided by the job's own {@code RUNNER} property (see {@link
     * JobRunQueueService#claimIfMatchesThisRunner}), read via {@link JobService#getAllJobs()}/{@link
     * JobService#getProperties(ch.uetlib.job.Job)} - the same per-job property cache pattern
     * as elsewhere in this codebase.
     */
    @SuppressWarnings("unchecked")
    private void stubJobRunnerKey(final JobRunnerKey runnerKey) {
        final var job = new TestJob();
        final var property = (JobProperty<Object>) (JobProperty<?>) RUNNER.createProperty(runnerKey);
        when(jobService.getAllJobs()).thenReturn(List.of(job));
        when(jobService.findJobId(TestJob.class)).thenReturn(Optional.of(1));
        when(jobService.getProperties(job)).thenReturn(new JobProperties(List.of(property)));
    }

    private static JobQueuedRun queuedRun(final int id, final Set<JobRunnerTaint> taints) {
        return queuedRun(id, taints, VERSION_TAG);
    }

    private static JobQueuedRun queuedRun(
            final int id, final Set<JobRunnerTaint> taints, final String versionTag) {
        final var now = Instant.now();
        return new JobQueuedRun(
                id,
                null,
                1,
                versionTag,
                JobRunQueueState.QUEUED,
                null,
                null,
                null,
                now,
                now,
                taints,
                new JobRunArguments());
    }
}
