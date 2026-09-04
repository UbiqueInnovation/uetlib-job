package ch.uetlib.job.service.executor;

import static ch.uetlib.job.model.properties.JobPropertyDefinitions.RUNNER;
import static ch.uetlib.job.model.properties.JobPropertyDefinitions.RUNNER_GROUP;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.client.info.JobSystemRunnerGroupInfo;
import ch.uetlib.job.model.executer.JobSubmissionStatus;
import ch.uetlib.job.model.executer.UnsuccessfulSubmissionResult;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.model.runner.conditions.JobRunnerTaint;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.info.JobRunnerInfoService;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    private static final int MAX_PAGES = 5;

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
                    PAGE_SIZE,
                    MAX_PAGES,
                    Duration.ofMinutes(5));

    {
        // Every queuedRun(...) below is tagged VERSION_TAG; matching this instance's own version is
        // otherwise a precondition for every claim attempt (see claimIfMatchesThisRunner), so stub
        // it once here rather than repeating it in every test.
        when(jobService.versionTag()).thenReturn(VERSION_TAG);
        // As is the job being known to this build - a row whose job it isn't gets cancelled instead
        // of routed, which is its own test below.
        when(jobService.findJobById(anyInt()))
                .thenReturn(Optional.of(new JobClassAndId(1, "ch.uetlib.job.TestJob")));
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
                                List.of(untaggedButDedicated),
                                PageRequest.of(0, PAGE_SIZE),
                                false));

        service.checkForRunnableAndSchedule();

        verify(jobService).hasRunner(JobRunnerKey.DEDICATED_K8S_JOB);
        verify(jobService).claimQueuedRun(untaggedButDedicated, INSTANCE_NAME);
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
        final var otherVersion = queuedRun(1, Set.of(JobRunnerTaint.from("gpu")), "2.0.0");
        final var sameVersion = queuedRun(2, Set.of(JobRunnerTaint.from("gpu")));
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

    @Test
    void aRowWhoseJobThisBuildDoesNotKnowIsCancelledOnceItHasBeenUnknownLongEnough() {
        // The version check has already passed, so nothing that may claim this row can resolve the
        // job either; leaving it QUEUED would retry it on every tick forever.
        final var unknownJob = agedQueuedRun(1, Duration.ofMinutes(30));
        when(jobService.findJobById(anyInt())).thenReturn(Optional.empty());
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(List.of(unknownJob), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService).cancelUnclaimableQueuedRun(unknownJob);
        verify(jobService, never()).claimQueuedRun(any(), any());
    }

    @Test
    void aRowWhoseJobIsNotYetKnownIsLeftAloneDuringTheGracePeriod() {
        // An instance builds its job registry at startup, so mid-rollout one that is about to be
        // replaced can briefly not know a job its replacement already registered. Cancelling on
        // sight would throw away a run the newer instance could have executed.
        final var justQueued = agedQueuedRun(1, Duration.ofSeconds(30));
        when(jobService.findJobById(anyInt())).thenReturn(Optional.empty());
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(List.of(justQueued), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService, never()).cancelUnclaimableQueuedRun(any());
    }

    @Test
    void keepsPagingPastRowsItMatchesButCannotTakeForLackOfCapacity() {
        // A saturated runner group at the front of the queue must not stop the scan: matching a row
        // is not the same as being able to take it, and rows for idle groups may sit behind it.
        final var noCapacity = queuedRun(1, Set.of());
        final var alsoNoCapacity = queuedRun(2, Set.of());
        stubNoCapacityForPlainRuns();
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(List.of(noCapacity), PageRequest.of(0, PAGE_SIZE), true));
        when(jobService.findQueuedRunsPage(PageRequest.of(1, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(
                                List.of(alsoNoCapacity), PageRequest.of(1, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService).findQueuedRunsPage(PageRequest.of(1, PAGE_SIZE));
        verify(jobService, never()).claimQueuedRun(any(), any());
    }

    @Test
    void stopsAfterThePageCapSoAnUntakeableQueueCostsABoundedNumberOfQueries() {
        final var nonMatching = queuedRun(1, Set.of(JobRunnerTaint.from("unadvertised")));
        when(jobService.findQueuedRunsPage(any()))
                .thenAnswer(
                        invocation ->
                                new SliceImpl<>(
                                        List.of(nonMatching), invocation.getArgument(0), true));

        service.checkForRunnableAndSchedule();

        verify(jobService, times(MAX_PAGES)).findQueuedRunsPage(any());
    }

    @Test
    void aRowThatBlowsUpDoesNotStopTheRestOfTheQueueFromBeingProcessed() {
        // One unprocessable row used to abort the whole scan.
        final var poison = queuedRun(1, Set.of(JobRunnerTaint.from("gpu")));
        final var healthy = queuedRun(2, Set.of(JobRunnerTaint.from("gpu")));
        stubDedicatedClaimAttempt();
        when(jobService.claimQueuedRun(poison, INSTANCE_NAME))
                .thenThrow(new IllegalStateException("boom"));
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(
                                List.of(poison, healthy), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService).claimQueuedRun(healthy, INSTANCE_NAME);
    }

    @Test
    void aHandoffContinuationIsRoutedByItsBoundRunsRunnerNotTheJobsRunnerProperty() {
        // The whole point of the handoff: BaseStateJob#runnerForProcessing repointed the run at
        // another runner, so the continuation must follow the run, not the job's RUNNER property.
        final var continuation = continuationRun(1, 42);
        stubDedicatedClaimAttempt();
        stubJobRunnerKey(JobRunnerKey.CURRENT);
        when(jobService.getJobRunMaybe(42))
                .thenReturn(Optional.of(jobRunOn(42, JobRunnerKey.DEDICATED_K8S_JOB)));
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(
                                List.of(continuation), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService).hasRunner(JobRunnerKey.DEDICATED_K8S_JOB);
        verify(jobService).claimQueuedRun(continuation, INSTANCE_NAME);
    }

    @Test
    void aHandoffContinuationFallsBackToTheJobsRunnerWhenItsRunHasVanished() {
        final var continuation = continuationRun(1, 42);
        stubPlainClaimAttempt();
        when(jobService.getJobRunMaybe(42)).thenReturn(Optional.empty());
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(
                                List.of(continuation), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService).claimQueuedRun(continuation, INSTANCE_NAME);
        verify(jobService, never()).hasRunner(JobRunnerKey.DEDICATED_K8S_JOB);
    }

    /** A runner group with every thread busy, so a plain match cannot actually be taken. */
    private void stubNoCapacityForPlainRuns() {
        when(jobService.getProperties(any(JobClassAndId.class)))
                .thenReturn(new JobProperties(List.of()));
        final var saturated = mock(JobSystemRunnerGroupInfo.class);
        when(saturated.identifier())
                .thenReturn(RUNNER_GROUP.defaultValue().orElseThrow().identifier());
        when(saturated.busyThreads()).thenReturn(4);
        when(saturated.currentPoolSize()).thenReturn(4);
        when(jobRunnerInfoService.getRunnerGroupsInfo()).thenReturn(List.of(saturated));
    }

    private void stubPlainClaimAttempt() {
        when(jobService.getProperties(any(JobClassAndId.class)))
                .thenReturn(new JobProperties(List.of()));
        when(jobService.claimQueuedRun(any(), any())).thenReturn(true);
        when(jobExecutorService.submit(any(), anyBoolean()))
                .thenReturn(new UnsuccessfulSubmissionResult(JobSubmissionStatus.FAILED));
    }

    /** A dedicated row this instance wins the claim for - i.e. it makes progress on it. */
    private void stubDedicatedClaimAttempt() {
        when(jobService.hasRunner(JobRunnerKey.DEDICATED_K8S_JOB)).thenReturn(true);
        when(jobService.claimQueuedRun(any(), any())).thenReturn(true);
        // Submission "fails" so the test needn't build a full JobRunContext; the row was still
        // claimed, which is what the scan keys off.
        when(jobExecutorService.submit(any(), anyBoolean()))
                .thenReturn(new UnsuccessfulSubmissionResult(JobSubmissionStatus.FAILED));
        stubJobRunnerKey(JobRunnerKey.DEDICATED_K8S_JOB);
    }

    /**
     * Which claim path even applies is decided by the job's own {@code RUNNER} property (see {@link
     * JobRunQueueService#claimIfMatchesThisRunner}), read per job id via {@link
     * JobService#propertyValueByJobId} and cached briefly by the service.
     */
    private void stubJobRunnerKey(final JobRunnerKey runnerKey) {
        when(jobService.propertyValueByJobId(RUNNER)).thenReturn(Map.of(1, runnerKey));
    }

    private static JobQueuedRun queuedRun(final int id, final Set<JobRunnerTaint> taints) {
        return queuedRun(id, taints, VERSION_TAG);
    }

    /** A {@code QUEUED} row last touched {@code age} ago. */
    private static JobQueuedRun agedQueuedRun(final int id, final Duration age) {
        final var touched = Instant.now().minus(age);
        return new JobQueuedRun(
                id,
                null,
                1,
                VERSION_TAG,
                JobRunQueueState.QUEUED,
                null,
                null,
                touched,
                touched,
                Set.of(),
                new JobRunArguments());
    }

    /** A {@code BaseStateJob} handoff row: still {@code QUEUED}, but bound to an existing run. */
    private static JobQueuedRun continuationRun(final int id, final int jobRunId) {
        final var now = Instant.now();
        return new JobQueuedRun(
                id,
                null,
                1,
                VERSION_TAG,
                JobRunQueueState.QUEUED,
                jobRunId,
                null,
                now,
                now,
                Set.of(),
                new JobRunArguments());
    }

    private static JobRun jobRunOn(final int id, final JobRunnerKey runnerKey) {
        return new JobRun(
                id,
                1,
                VERSION_TAG,
                Instant.now(),
                null,
                JobRunState.SCHEDULED,
                runnerKey,
                null,
                null);
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
                now,
                now,
                taints,
                new JobRunArguments());
    }
}
