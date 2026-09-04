package ch.uetlib.job.service.executor;

import static ch.uetlib.job.model.properties.JobPropertyDefinitions.RUNNER;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobRunContext;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.executer.JobSubmissionStatus;
import ch.uetlib.job.model.executer.SuccessfulSubmissionResult;
import ch.uetlib.job.model.executer.UnsuccessfulSubmissionResult;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.model.runner.conditions.JobRunnerTaint;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.executor.runners.CurrentJobRunner;
import ch.uetlib.job.service.instance.JobInstanceIdentity;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Taint matching (via the dedicated path, which skips the capacity check), paging, the no-overlap
 * guard, that only a run able to start right away is claimed, and that the poll and the NOTIFY path
 * take turns per row.
 */
class JobRunQueueServiceTest {

    private static final String INSTANCE_NAME = "test-instance";
    private static final String VERSION_TAG = "1.0.0";
    private static final int PAGE_SIZE = 2;
    private static final int MAX_PAGES = 5;

    private final JobService jobService = mock(JobService.class);
    private final CurrentJobRunner currentJobRunner = mock(CurrentJobRunner.class);
    private final JobExecutorService jobExecutorService = mock(JobExecutorService.class);

    private final JobRunQueueService service =
            new JobRunQueueService(
                    jobService,
                    currentJobRunner,
                    jobExecutorService,
                    new JobInstanceIdentity(INSTANCE_NAME, Set.of("gpu"), Instant.now()),
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
        // And a claim needs the run to be able to start here right away: a free thread, and its
        // job allowed to start. The tests about either override these.
        when(currentJobRunner.hasCapacity(any())).thenReturn(true);
        when(jobExecutorService.canStart(any())).thenReturn(true);
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
    void theNotifyPathWaitsForAClaimInProgressInsteadOfCheckingAlongsideIt() throws Exception {
        // The NOTIFY path skips the poll's guard, so it may arrive while the poll takes the last
        // free thread. Checking capacity, or whether the job may start, before that submission is
        // done would let both pass.
        final var polled = queuedRun(1, Set.of());
        final var notified = queuedRun(2, Set.of());
        stubPlainClaimAttempt();
        final var finishSubmission = new CountDownLatch(1);
        when(jobExecutorService.submit(eq(polled), anyBoolean()))
                .thenAnswer(
                        invocation -> {
                            finishSubmission.await(5, TimeUnit.SECONDS);
                            return new UnsuccessfulSubmissionResult(JobSubmissionStatus.FAILED);
                        });
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(new SliceImpl<>(List.of(polled), PageRequest.of(0, PAGE_SIZE), false));
        when(jobService.findQueuedByIds(List.of(2))).thenReturn(List.of(notified));

        final var poll = CompletableFuture.runAsync(service::checkForRunnableAndSchedule);
        verify(jobExecutorService, timeout(5_000)).submit(eq(polled), anyBoolean());
        final var notify =
                CompletableFuture.runAsync(() -> service.checkForRunnable(List.of(Pair.of(1, 2))));

        verify(jobExecutorService, after(300).never()).canStart(notified);
        finishSubmission.countDown();
        poll.get(5, TimeUnit.SECONDS);
        notify.get(5, TimeUnit.SECONDS);
        verify(jobExecutorService).canStart(notified);
        verify(jobService).claimQueuedRun(notified, INSTANCE_NAME);
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
    void aRowWhoseJobCannotStartYetIsNotClaimedAndDoesNotStopTheScan() {
        // E.g. its job is still running and disallows parallel runs: claiming it would only bounce
        // it straight back.
        final var blocked = queuedRun(1, Set.of());
        final var startable = queuedRun(2, Set.of());
        stubPlainClaimAttempt();
        when(jobExecutorService.canStart(blocked)).thenReturn(false);
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(new SliceImpl<>(List.of(blocked), PageRequest.of(0, PAGE_SIZE), true));
        when(jobService.findQueuedRunsPage(PageRequest.of(1, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(List.of(startable), PageRequest.of(1, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService, never()).claimQueuedRun(blocked, INSTANCE_NAME);
        verify(jobExecutorService, never()).submit(eq(blocked), anyBoolean());
        verify(jobService).claimQueuedRun(startable, INSTANCE_NAME);
    }

    @Test
    void aRowThatCannotStartYetIsKeptFromExpiringAtMostOncePerMinute() {
        // QUEUE_TIMEOUT counts from a row's last activity, so a run waiting behind its job's
        // current run must be marked as still wanted - but not with a write on every check.
        final var waitingLong = agedQueuedRun(1, Duration.ofMinutes(5));
        final var justMarked = agedQueuedRun(2, Duration.ofSeconds(10));
        stubPlainClaimAttempt();
        when(jobExecutorService.canStart(any())).thenReturn(false);
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(
                                List.of(waitingLong, justMarked),
                                PageRequest.of(0, PAGE_SIZE),
                                false));

        service.checkForRunnableAndSchedule();

        verify(jobService).touchWaitingQueuedRun(waitingLong);
        verify(jobService, never()).touchWaitingQueuedRun(justMarked);
        verify(jobService, never()).claimQueuedRun(any(), any());
    }

    @Test
    void aRowIsNotClaimedWhileItsRunnerGroupIsFullButKeepsWaiting() {
        final var waiting = agedQueuedRun(1, Duration.ofMinutes(5));
        stubNoCapacityForPlainRuns();
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(new SliceImpl<>(List.of(waiting), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService, never()).claimQueuedRun(any(), any());
        verify(jobService).touchWaitingQueuedRun(waiting);
        // A full runner group decides it already; whether the job may start doesn't matter.
        verify(jobExecutorService, never()).canStart(any());
    }

    @Test
    void aRowThisInstanceCannotRunIsNeitherClaimedNorKeptAlive() {
        // Only an instance that could run a row keeps it alive; QUEUE_TIMEOUT must still cancel a
        // row nothing can take.
        final var otherTaints =
                agedQueuedRun(
                        1, Duration.ofMinutes(5), Set.of(JobRunnerTaint.from("unadvertised")));
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(List.of(otherTaints), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService, never()).claimQueuedRun(any(), any());
        verify(jobService, never()).touchWaitingQueuedRun(any());
    }

    @Test
    void aDedicatedRowOnAnInstanceWithoutADedicatedRunnerIsNeitherClaimedNorKeptAlive() {
        final var dedicated = agedQueuedRun(1, Duration.ofMinutes(5));
        stubJobRunnerKey(JobRunnerKey.DEDICATED_K8S_JOB);
        when(jobService.hasRunner(JobRunnerKey.DEDICATED_K8S_JOB)).thenReturn(false);
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(
                        new SliceImpl<>(List.of(dedicated), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService, never()).claimQueuedRun(any(), any());
        verify(jobService, never()).touchWaitingQueuedRun(any());
    }

    @Test
    void aClaimWhoseSubmissionFailsIsReleasedForARetry() {
        final var row = queuedRun(1, Set.of());
        stubPlainClaimAttempt();
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(new SliceImpl<>(List.of(row), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService).releaseQueuedRun(row);
    }

    @Test
    void aClaimRefusedAtSubmissionIsReleased() {
        // canStart said yes, but another instance started the job before this one submitted.
        final var row = queuedRun(1, Set.of());
        stubPlainClaimAttempt();
        when(jobExecutorService.submit(any(), anyBoolean()))
                .thenReturn(new UnsuccessfulSubmissionResult(JobSubmissionStatus.DISALLOWED));
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(new SliceImpl<>(List.of(row), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService).releaseQueuedRun(row);
    }

    @Test
    void aDispatchedClaimIsNotReleased() {
        final var row = queuedRun(1, Set.of());
        stubPlainClaimAttempt();
        when(jobExecutorService.submit(any(), anyBoolean()))
                .thenReturn(
                        new SuccessfulSubmissionResult(
                                JobRunnerKey.CURRENT, mock(JobRunContext.class), 99));
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(new SliceImpl<>(List.of(row), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableAndSchedule();

        verify(jobService).claimQueuedRun(row, INSTANCE_NAME);
        verify(jobService, never()).releaseQueuedRun(any());
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

    /** A runner group with every thread taken, so a plain match cannot actually be taken. */
    private void stubNoCapacityForPlainRuns() {
        when(jobService.getProperties(any(JobClassAndId.class)))
                .thenReturn(new JobProperties(List.of()));
        when(currentJobRunner.hasCapacity(any())).thenReturn(false);
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

    /** Stubs the job's {@code RUNNER} property, which picks the claim path. */
    private void stubJobRunnerKey(final JobRunnerKey runnerKey) {
        when(jobService.propertyValueByJobId(RUNNER)).thenReturn(Map.of(1, runnerKey));
    }

    private static JobQueuedRun queuedRun(final int id, final Set<JobRunnerTaint> taints) {
        return queuedRun(id, taints, VERSION_TAG);
    }

    /** A {@code QUEUED} row last touched {@code age} ago. */
    private static JobQueuedRun agedQueuedRun(final int id, final Duration age) {
        return agedQueuedRun(id, age, Set.of());
    }

    private static JobQueuedRun agedQueuedRun(
            final int id, final Duration age, final Set<JobRunnerTaint> taints) {
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
                taints,
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
