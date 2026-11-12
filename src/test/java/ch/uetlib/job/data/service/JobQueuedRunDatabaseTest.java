package ch.uetlib.job.data.service;

import static org.junit.jupiter.api.Assertions.*;

import ch.uetlib.job.data.BaseJobDatabaseTest;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.properties.enums.JobQueueBehavior;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.service.inputs.JobRunInputManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The queue's ordered, paged listing query on every database; taint matching is covered by {@code
 * JobRunQueueServiceTest}.
 */
class JobQueuedRunDatabaseTest extends BaseJobDatabaseTest {

    private static final int TEST_JOB_ID = 1;

    @Autowired JobDataService jobDataService;
    @Autowired JdbcTemplate jobJdbcTemplate;
    @MockitoBean JobRunInputManager jobRunInputManager;

    @Test
    void onlyQueuedStateRunsAreReturned() {
        final var stillQueued =
                jobDataService.queueNewRun(
                        TEST_JOB_ID,
                        null,
                        "1.0.0",
                        new JobRunArguments(),
                        Set.of(),
                        JobQueueBehavior.ALLOW_MULTIPLE);
        final var claimed =
                jobDataService.queueNewRun(
                        TEST_JOB_ID,
                        null,
                        "1.0.0",
                        new JobRunArguments(),
                        Set.of(),
                        JobQueueBehavior.ALLOW_MULTIPLE);
        jobDataService.claimQueuedRun(claimed, "test-runner");

        final var page = jobDataService.findQueuedRunsPage(PageRequest.of(0, 10));

        final var ids = page.getContent().stream().map(JobQueuedRun::jobQueuedRunId).toList();
        assertTrue(ids.contains(stillQueued.jobQueuedRunId()));
        assertFalse(
                ids.contains(claimed.jobQueuedRunId()),
                "a CLAIMED row must not appear in the queued listing");
    }

    @Test
    void ordersByPriorityDescending() {
        // Not asserting oldest-first *within* the same priority here: created_at has whole-second
        // resolution on some dialects, so two rows queued microseconds apart in a test can tie.
        final var low =
                jobDataService.queueNewRun(
                        TEST_JOB_ID,
                        1,
                        "1.0.0",
                        new JobRunArguments(),
                        Set.of(),
                        JobQueueBehavior.ALLOW_MULTIPLE);
        final var high =
                jobDataService.queueNewRun(
                        TEST_JOB_ID,
                        5,
                        "1.0.0",
                        new JobRunArguments(),
                        Set.of(),
                        JobQueueBehavior.ALLOW_MULTIPLE);

        final var page = jobDataService.findQueuedRunsPage(PageRequest.of(0, 10));

        final var ids = page.getContent().stream().map(JobQueuedRun::jobQueuedRunId).toList();
        assertTrue(
                ids.indexOf(high.jobQueuedRunId()) < ids.indexOf(low.jobQueuedRunId()),
                "the higher-priority row must be ordered before the lower-priority one");
    }

    @Test
    void pagesWhenMoreRowsThanPageSize() {
        final var queued =
                IntStream.range(0, 3)
                        .mapToObj(
                                i ->
                                        jobDataService.queueNewRun(
                                                TEST_JOB_ID,
                                                null,
                                                "1.0.0",
                                                new JobRunArguments(),
                                                Set.of(),
                                                JobQueueBehavior.ALLOW_MULTIPLE))
                        .toList();

        final var firstPage = jobDataService.findQueuedRunsPage(PageRequest.of(0, 2));
        assertEquals(2, firstPage.getContent().size());
        assertTrue(firstPage.hasNext(), "3 queued rows with page size 2 must have a next page");

        final var secondPage = jobDataService.findQueuedRunsPage(PageRequest.of(1, 2));
        assertEquals(1, secondPage.getContent().size());
        assertFalse(secondPage.hasNext());

        final var idsAcrossBothPages =
                Stream.concat(firstPage.getContent().stream(), secondPage.getContent().stream())
                        .map(JobQueuedRun::jobQueuedRunId)
                        .toList();
        queued.forEach(run -> assertTrue(idsAcrossBothPages.contains(run.jobQueuedRunId())));
    }

    @Test
    void claimQueuedRunMovesQueuedToClaimedAndRejectsASecondClaimOfTheSameSnapshot() {
        final var run = queueOne();

        assertTrue(jobDataService.claimQueuedRun(run, "runner-a"));
        assertFalse(
                jobDataService.claimQueuedRun(run, "runner-b"),
                "the row is now CLAIMED, so a claim attempt using the pre-claim (QUEUED) snapshot"
                        + " must fail");
    }

    @Test
    void setRunIdMovesClaimedToRunningWithTheGivenJobRunId() {
        final var run = queueOne();
        jobDataService.claimQueuedRun(run, "runner-a");

        jobDataService.setRunId(run, EXISTING_JOB_RUN_ID);

        final var running = jobDataService.findQueuedRunsByState(JobRunQueueState.RUNNING);
        final var reloaded =
                running.stream()
                        .filter(r -> r.jobQueuedRunId() == run.jobQueuedRunId())
                        .findFirst()
                        .orElseThrow();
        assertEquals(EXISTING_JOB_RUN_ID, reloaded.jobRunId());
    }

    @Test
    void updateQueuedRunPriorityUpdatesAQueuedRow() {
        final var run = queueOne();

        assertTrue(jobDataService.updateQueuedRunPriority(run.jobQueuedRunId(), 42));

        final var reloaded = findInQueue(run.jobQueuedRunId()).orElseThrow();
        assertEquals(42, reloaded.priority());
    }

    @Test
    void updateQueuedRunPriorityCanClearTheOverrideBackToNull() {
        final var run = queueOne();
        jobDataService.updateQueuedRunPriority(run.jobQueuedRunId(), 42);

        assertTrue(jobDataService.updateQueuedRunPriority(run.jobQueuedRunId(), null));

        assertNull(findInQueue(run.jobQueuedRunId()).orElseThrow().priority());
    }

    @Test
    void updateQueuedRunPriorityNeverUpdatesARunningRow() {
        final var runningRunId = queueClaimAndRunToRunning();

        assertFalse(jobDataService.updateQueuedRunPriority(runningRunId, 42));
    }

    @Test
    void updateQueuedRunPriorityNeverUpdatesAClaimedRow() {
        final var run = queueOne();
        jobDataService.claimQueuedRun(run, "runner-a");

        assertFalse(jobDataService.updateQueuedRunPriority(run.jobQueuedRunId(), 42));
    }

    @Test
    void deleteQueuedRunIfQueuedDeletesAQueuedRow() {
        final var run = queueOne();

        assertTrue(jobDataService.deleteQueuedRunIfQueued(run.jobQueuedRunId()));
        assertTrue(findInQueue(run.jobQueuedRunId()).isEmpty());
    }

    @Test
    void deleteQueuedRunIfQueuedNeverDeletesARunningRow() {
        final var runningRunId = queueClaimAndRunToRunning();

        assertFalse(jobDataService.deleteQueuedRunIfQueued(runningRunId));
        assertEquals(
                1,
                jobDataService.findQueuedRunsByState(JobRunQueueState.RUNNING).stream()
                        .filter(r -> r.jobQueuedRunId() == runningRunId)
                        .count());
    }

    @Test
    void deleteQueuedRunIfQueuedNeverDeletesAClaimedRow() {
        final var run = queueOne();
        jobDataService.claimQueuedRun(run, "runner-a");

        assertFalse(jobDataService.deleteQueuedRunIfQueued(run.jobQueuedRunId()));
        assertEquals(
                1,
                jobDataService.findQueuedRunsByState(JobRunQueueState.CLAIMED).stream()
                        .filter(r -> r.jobQueuedRunId() == run.jobQueuedRunId())
                        .count(),
                "a CLAIMED row must not be deletable - cancel its job run instead");
    }

    @Test
    void deleteQueuedRunsBulkDeletesOnlyRowsInTheGivenStates() {
        final var toDelete = queueOne();
        jobDataService.claimQueuedRun(toDelete, "runner-a");
        final var stillQueued = queueOne();

        final var removed =
                jobDataService.deleteQueuedRuns(
                        List.of(TEST_JOB_ID), Set.of(JobRunQueueState.CLAIMED));

        assertEquals(1, removed);
        assertTrue(findInQueue(toDelete.jobQueuedRunId()).isEmpty());
        assertTrue(
                findInQueue(stillQueued.jobQueuedRunId()).isPresent(),
                "a row not in the requested state(s) must not be touched");
    }

    @Test
    void findOldestAndNewestQueuedRunReturnTheSameSingleRowWhenOnlyOneIsQueued() {
        final var only = queueOne();

        assertEquals(
                only.jobQueuedRunId(),
                jobDataService.findOldestQueuedRun().orElseThrow().jobQueuedRunId());
        assertEquals(
                only.jobQueuedRunId(),
                jobDataService.findNewestQueuedRun().orElseThrow().jobQueuedRunId());
    }

    @Test
    void findOldestAndNewestQueuedRunAreEmptyWhenNothingIsQueued() {
        assertTrue(jobDataService.findOldestQueuedRun().isEmpty());
        assertTrue(jobDataService.findNewestQueuedRun().isEmpty());
    }

    @Test
    void findQueuedRunsFiltersByJobAndState() {
        final var queued = queueOne();
        final var claimed = queueOne();
        jobDataService.claimQueuedRun(claimed, "runner-a");

        final var onlyQueued =
                jobDataService.findQueuedRuns(
                        PageRequest.of(0, 10),
                        List.of(TEST_JOB_ID),
                        Set.of(JobRunQueueState.QUEUED),
                        Optional.empty());

        final var ids = onlyQueued.stream().map(JobQueuedRun::jobQueuedRunId).toList();
        assertTrue(ids.contains(queued.jobQueuedRunId()));
        assertFalse(ids.contains(claimed.jobQueuedRunId()));
    }

    @Test
    void queueNewRunNeverDeletesARunningRowWhenDedupingToTheLatest() {
        final var runningRunId = queueClaimAndRunToRunning();

        jobDataService.queueNewRun(
                TEST_JOB_ID,
                null,
                "1.0.0",
                new JobRunArguments(),
                Set.of(),
                JobQueueBehavior.REPLACE_RUN);

        assertTrue(
                findInQueue(runningRunId).isPresent(),
                "a job's trigger firing again must not delete its already-RUNNING queue row");
    }

    @Test
    void queueNewRunStillQueuesANewRunEvenWhileOneIsAlreadyRunningByDefault() {
        final var runningRunId = queueClaimAndRunToRunning();

        final var newlyQueued =
                jobDataService.queueNewRun(
                        TEST_JOB_ID,
                        null,
                        "1.0.0",
                        new JobRunArguments(),
                        Set.of(),
                        JobQueueBehavior.REPLACE_RUN);

        final var allRowsForJob = allQueueRowsForJob();
        assertEquals(
                Set.of(runningRunId, newlyQueued.jobQueuedRunId()),
                allRowsForJob.stream()
                        .map(JobQueuedRun::jobQueuedRunId)
                        .collect(Collectors.toSet()),
                "a RUNNING row isn't 'waiting' - ALLOW_MULTIPLE_WAITING_QUEUED_RUNS being off must"
                        + " only dedupe against other waiting (QUEUED/CLAIMED) rows, not block a new"
                        + " run from being queued behind an already-running one");
        assertEquals(
                JobRunQueueState.QUEUED,
                findInQueue(newlyQueued.jobQueuedRunId()).orElseThrow().queueState());
    }

    @Test
    void queueNewRunDeletesAnExistingWaitingRowWhenDedupingToTheLatestByDefault() {
        final var previouslyQueued = queueOne();

        jobDataService.queueNewRun(
                TEST_JOB_ID,
                null,
                "1.0.0",
                new JobRunArguments(),
                Set.of(),
                JobQueueBehavior.REPLACE_RUN);

        assertTrue(findInQueue(previouslyQueued.jobQueuedRunId()).isEmpty());
    }

    @Test
    void queueNewRunNeverDeletesAClaimedRowWhenDedupingToTheLatest() {
        // A claimed row is already being submitted; deleting it would orphan its job run.
        final var claimed = queueOne();
        jobDataService.claimQueuedRun(claimed, "runner-a");

        final var newlyQueued =
                jobDataService.queueNewRun(
                        TEST_JOB_ID,
                        null,
                        "1.0.0",
                        new JobRunArguments(),
                        Set.of(),
                        JobQueueBehavior.REPLACE_RUN);

        final var claimedRow =
                allQueueRowsForJob().stream()
                        .filter(r -> r.jobQueuedRunId() == claimed.jobQueuedRunId())
                        .findFirst();
        assertTrue(claimedRow.isPresent(), "the claimed row must survive a REPLACE_RUN enqueue");
        assertEquals(JobRunQueueState.CLAIMED, claimedRow.orElseThrow().queueState());
        assertEquals(
                JobRunQueueState.QUEUED,
                findInQueue(newlyQueued.jobQueuedRunId()).orElseThrow().queueState(),
                "the new run queues behind the claimed one");
    }

    @Test
    void setRunIdOnlyEverLinksAClaimedRow() {
        final var stillQueued = queueOne();

        assertFalse(jobDataService.setRunId(stillQueued, EXISTING_JOB_RUN_ID));
        assertEquals(
                JobRunQueueState.QUEUED,
                findInQueue(stillQueued.jobQueuedRunId()).orElseThrow().queueState());
    }

    @Test
    void queueReprocessingRunReQueuesTheRunsOwnRowRatherThanAddingASecondOne() {
        // The handoff must send the run's own RUNNING row back to QUEUED, still bound to it.
        // Inserting a second row - or bailing out because one exists, as it used to - leaves the
        // run stuck in SCHEDULED with nothing to resume it.
        final var runningRunId = queueClaimAndRunToRunning();

        final var reprocessing =
                jobDataService.queueReprocessingRun(
                        TEST_JOB_ID,
                        "1.0.0",
                        new JobRunArguments(),
                        Set.of(),
                        EXISTING_JOB_RUN_ID,
                        JobRunnerKey.DEDICATED_K8S_JOB);

        assertEquals(
                runningRunId, reprocessing.jobQueuedRunId(), "the run keeps its one queue row");
        assertEquals(JobRunQueueState.QUEUED, reprocessing.queueState());
        assertEquals(EXISTING_JOB_RUN_ID, reprocessing.jobRunId());
        assertEquals(
                1,
                allQueueRowsForJob().size(),
                "the handoff must not leave a second row behind for the same run");
    }

    @Test
    void queueReprocessingRunInsertsARowForARunThatHasNoQueueRowAtAll() {
        // A run started outside the queue - a standalone --job CLI process - has no row to
        // re-queue.
        final var reprocessing =
                jobDataService.queueReprocessingRun(
                        TEST_JOB_ID,
                        "1.0.0",
                        new JobRunArguments(),
                        Set.of(),
                        EXISTING_JOB_RUN_ID,
                        JobRunnerKey.DEDICATED_K8S_JOB);

        assertEquals(JobRunQueueState.QUEUED, reprocessing.queueState());
        assertEquals(EXISTING_JOB_RUN_ID, reprocessing.jobRunId());
    }

    @Test
    void queueNewRunKeepsAnExistingWaitingRowWhenMultipleWaitingRunsAreAllowed() {
        final var previouslyQueued = queueOne();

        final var newlyQueued =
                jobDataService.queueNewRun(
                        TEST_JOB_ID,
                        null,
                        "1.0.0",
                        new JobRunArguments(),
                        Set.of(),
                        JobQueueBehavior.ALLOW_MULTIPLE);

        final var allRowsForJob = allQueueRowsForJob();
        assertEquals(
                Set.of(previouslyQueued.jobQueuedRunId(), newlyQueued.jobQueuedRunId()),
                allRowsForJob.stream()
                        .map(JobQueuedRun::jobQueuedRunId)
                        .collect(Collectors.toSet()),
                "with ALLOW_MULTIPLE, an existing QUEUED (waiting) row must not be replaced");
    }

    @Test
    void queueNewRunWithDiscardNewKeepsTheExistingWaitingRowAndReturnsIt() {
        final var previouslyQueued = queueOne();

        final var result =
                jobDataService.tryQueueNewRun(
                        TEST_JOB_ID,
                        null,
                        "1.0.0",
                        new JobRunArguments(),
                        Set.of(),
                        JobQueueBehavior.DISCARD_NEW);

        assertFalse(result.queued());
        assertEquals(previouslyQueued.jobQueuedRunId(), result.run().jobQueuedRunId());
        assertEquals(
                List.of(previouslyQueued.jobQueuedRunId()),
                allQueueRowsForJob().stream().map(JobQueuedRun::jobQueuedRunId).toList(),
                "DISCARD_NEW must not add a second waiting row");
    }

    @Test
    void queueNewRunWithDiscardNewStillQueuesWhenNothingIsWaiting() {
        final var result =
                jobDataService.tryQueueNewRun(
                        TEST_JOB_ID,
                        null,
                        "1.0.0",
                        new JobRunArguments(),
                        Set.of(),
                        JobQueueBehavior.DISCARD_NEW);

        assertTrue(result.queued());
        assertEquals(
                JobRunQueueState.QUEUED,
                findInQueue(result.run().jobQueuedRunId()).orElseThrow().queueState());
    }

    @Test
    void queueNewRunWithDiscardIfRunningKeepsTheRunningRowAndDoesNotQueue() {
        final var runningRunId = queueClaimAndRunToRunning();

        final var result =
                jobDataService.tryQueueNewRun(
                        TEST_JOB_ID,
                        null,
                        "1.0.0",
                        new JobRunArguments(),
                        Set.of(),
                        JobQueueBehavior.DISCARD_IF_RUNNING);

        assertFalse(result.queued());
        assertEquals(runningRunId, result.run().jobQueuedRunId());
        assertEquals(
                List.of(runningRunId),
                allQueueRowsForJob().stream().map(JobQueuedRun::jobQueuedRunId).toList(),
                "DISCARD_IF_RUNNING must not queue a run behind an already-running one");
    }

    @Test
    void resetStaleClaimsResetsClaimsOlderThanTheGivenThreshold() {
        final var run = queueOne();
        jobDataService.claimQueuedRun(run, "runner-a");

        // updatedAt was just set to Instant.now() by claimQueuedRun, so a staleBefore in the
        // future makes it "stale" without needing to fabricate an old timestamp.
        final var reset =
                jobDataService.resetStaleClaims(Map.of(TEST_JOB_ID, Instant.now().plusSeconds(60)));

        assertEquals(1, reset);
        assertTrue(findInQueue(run.jobQueuedRunId()).isPresent());
    }

    @Test
    void resetStaleClaimsLeavesFreshClaimsAlone() {
        final var run = queueOne();
        jobDataService.claimQueuedRun(run, "runner-a");

        final var reset =
                jobDataService.resetStaleClaims(
                        Map.of(TEST_JOB_ID, Instant.now().minusSeconds(60)));

        assertEquals(0, reset);
    }

    @Test
    void expireStaleQueuedRunsCancelsQueuedRunsOlderThanTheGivenThreshold() {
        final var run = queueOne();

        // createdAt/updatedAt were just set to Instant.now(), so a staleBefore in the future makes
        // it "stale" without needing to fabricate an old timestamp - same trick as
        // resetStaleClaimsResetsClaimsOlderThanTheGivenThreshold.
        final var expired =
                jobDataService.expireStaleQueuedRuns(
                        Map.of(TEST_JOB_ID, Instant.now().plusSeconds(60)));

        assertEquals(1, expired);
        assertEquals(
                1,
                jobDataService.findQueuedRunsByState(JobRunQueueState.CANCELLED).stream()
                        .filter(r -> r.jobQueuedRunId() == run.jobQueuedRunId())
                        .count());
    }

    @Test
    void expireStaleQueuedRunsLeavesFreshQueuedRunsAlone() {
        queueOne();

        final var expired =
                jobDataService.expireStaleQueuedRuns(
                        Map.of(TEST_JOB_ID, Instant.now().minusSeconds(60)));

        assertEquals(0, expired);
    }

    @Test
    void expireStaleQueuedRunsNeverTouchesAClaimedRow() {
        final var run = queueOne();
        jobDataService.claimQueuedRun(run, "runner-a");

        final var expired =
                jobDataService.expireStaleQueuedRuns(
                        Map.of(TEST_JOB_ID, Instant.now().plusSeconds(60)));

        assertEquals(
                0, expired, "only QUEUED rows are eligible - a CLAIMED row must not be cancelled");
    }

    @Test
    void deleteOldCancelledQueuedRunsDeletesCancelledRunsPastRetention() {
        final var run = queueOne();
        jobDataService.expireStaleQueuedRuns(Map.of(TEST_JOB_ID, Instant.now().plusSeconds(60)));

        // updatedAt was just bumped to Instant.now() by expireStaleQueuedRuns, so a retainAfter in
        // the future makes it "past retention" without needing to fabricate an old timestamp.
        final var deleted =
                jobDataService.deleteOldCancelledQueuedRuns(
                        Map.of(TEST_JOB_ID, Instant.now().plusSeconds(60)));

        assertEquals(1, deleted);
        assertTrue(
                jobDataService.findQueuedRunsByState(JobRunQueueState.CANCELLED).stream()
                        .noneMatch(r -> r.jobQueuedRunId() == run.jobQueuedRunId()));
    }

    @Test
    void deleteOldCancelledQueuedRunsLeavesRecentCancelledRunsAlone() {
        final var run = queueOne();
        jobDataService.expireStaleQueuedRuns(Map.of(TEST_JOB_ID, Instant.now().plusSeconds(60)));

        final var deleted =
                jobDataService.deleteOldCancelledQueuedRuns(
                        Map.of(TEST_JOB_ID, Instant.now().minusSeconds(60)));

        assertEquals(0, deleted);
        assertEquals(
                1,
                jobDataService.findQueuedRunsByState(JobRunQueueState.CANCELLED).stream()
                        .filter(r -> r.jobQueuedRunId() == run.jobQueuedRunId())
                        .count());
    }

    @Test
    void acknowledgeQueuedRunDeletesACancelledRow() {
        final var run = queueOne();
        jobDataService.expireStaleQueuedRuns(Map.of(TEST_JOB_ID, Instant.now().plusSeconds(60)));

        assertTrue(jobDataService.acknowledgeQueuedRun(run.jobQueuedRunId()));
        assertTrue(
                jobDataService.findQueuedRunsByState(JobRunQueueState.CANCELLED).stream()
                        .noneMatch(r -> r.jobQueuedRunId() == run.jobQueuedRunId()));
    }

    @Test
    void acknowledgeQueuedRunNeverDeletesAQueuedRow() {
        final var run = queueOne();

        assertFalse(jobDataService.acknowledgeQueuedRun(run.jobQueuedRunId()));
        assertTrue(findInQueue(run.jobQueuedRunId()).isPresent());
    }

    @Test
    void acknowledgeAllCancelledQueuedRunsOnlyEverTouchesCancelledRows() {
        final var toCancel = queueOne();
        jobDataService.expireStaleQueuedRuns(Map.of(TEST_JOB_ID, Instant.now().plusSeconds(60)));
        // Queued *after* the expiry call above, so this one is untouched by it and stays QUEUED.
        final var stillQueued = queueOne();

        final var acknowledged = jobDataService.acknowledgeAllCancelledQueuedRuns();

        assertEquals(1, acknowledged);
        assertTrue(
                jobDataService.findQueuedRunsByState(JobRunQueueState.CANCELLED).stream()
                        .noneMatch(r -> r.jobQueuedRunId() == toCancel.jobQueuedRunId()));
        assertTrue(
                findInQueue(stillQueued.jobQueuedRunId()).isPresent(),
                "acknowledge-all must only ever touch CANCELLED rows");
    }

    @Test
    void queueRunningRunInsertsANewRowWhenNoneExistsForTheGivenJobRunId() {
        final var queued =
                jobDataService.queueRunningRun(
                        TEST_JOB_ID,
                        "1.0.0",
                        new JobRunArguments(),
                        Set.of(),
                        EXISTING_JOB_RUN_ID,
                        "runner-a");

        assertEquals(JobRunQueueState.RUNNING, queued.queueState());
        assertEquals(EXISTING_JOB_RUN_ID, queued.jobRunId());
    }

    @Test
    void queueRunningRunRecordsTheDedicatedRunnerOnTheExistingRow() {
        final var runningRunId = queueClaimAndRunToRunning();

        final var second =
                jobDataService.queueRunningRun(
                        TEST_JOB_ID,
                        "1.0.0",
                        new JobRunArguments(),
                        Set.of(),
                        EXISTING_JOB_RUN_ID,
                        "runner-b");

        assertEquals(
                runningRunId,
                second.jobQueuedRunId(),
                "a job run already claimed and RUNNING must not get a second, duplicate queue row");
        assertEquals("runner-b", second.runner());
        assertEquals("runner-b", findInQueue(runningRunId).orElseThrow().runner());
        assertEquals(JobRunQueueState.RUNNING, second.queueState());
    }

    /** An existing {@code t_job_run} row from the fixtures, valid as {@code fk_job_run_id}. */
    private static final int EXISTING_JOB_RUN_ID = 2;

    @Test
    void replaceRunNeverDeletesAHandoffContinuation() {
        // The regression this guards: a continuation is QUEUED but bound to a run that is already
        // under way. Deleting it strands that run in SCHEDULED with nothing to resume it.
        final var continuation = queueHandoffContinuation();

        jobDataService.queueNewRun(
                TEST_JOB_ID,
                null,
                "1.0.0",
                new JobRunArguments(),
                Set.of(),
                JobQueueBehavior.REPLACE_RUN);

        assertTrue(
                findInQueue(continuation.jobQueuedRunId()).isPresent(),
                "the continuation of an in-flight run must survive a REPLACE_RUN enqueue");
        assertEquals(2, allQueueRowsForJob().size(), "the new run queues alongside it");
    }

    @Test
    void discardNewIsNotBlockedByAHandoffContinuation() {
        // A continuation is not a run waiting to start, so it must not stand in for one: the new
        // request would otherwise be silently dropped and the caller handed someone else's run.
        final var continuation = queueHandoffContinuation();

        final var queued =
                jobDataService.queueNewRun(
                        TEST_JOB_ID,
                        null,
                        "1.0.0",
                        new JobRunArguments(),
                        Set.of(),
                        JobQueueBehavior.DISCARD_NEW);

        assertNotEquals(continuation.jobQueuedRunId(), queued.jobQueuedRunId());
        assertNull(queued.jobRunId(), "a genuinely new run was queued, not the continuation");
    }

    @Test
    void deleteQueuedRunIfQueuedNeverDeletesAHandoffContinuation() {
        final var continuation = queueHandoffContinuation();

        assertFalse(jobDataService.deleteQueuedRunIfQueued(continuation.jobQueuedRunId()));
        assertTrue(findInQueue(continuation.jobQueuedRunId()).isPresent());
    }

    @Test
    void bulkDeleteQueuedRunsNeverDeletesAHandoffContinuation() {
        final var continuation = queueHandoffContinuation();
        final var fresh = queueOne();

        final var removed =
                jobDataService.deleteQueuedRuns(
                        List.of(TEST_JOB_ID), Set.of(JobRunQueueState.QUEUED));

        assertEquals(1, removed, "only the fresh run is removable");
        assertTrue(findInQueue(fresh.jobQueuedRunId()).isEmpty());
        assertTrue(findInQueue(continuation.jobQueuedRunId()).isPresent());
    }

    /**
     * A run dispatched through the queue that then handed its processing phase to another runner.
     */
    @Test
    void aWaitingRowThatIsTouchedDoesNotExpireButAForgottenOneDoes() {
        // An instance that could run a row, just not yet, touches it, and QUEUE_TIMEOUT counts from
        // there - so only rows nothing wants get cancelled.
        final var waiting = queueOne();
        final var forgotten = queueOne();
        // A day rather than minutes: raw JDBC and Hibernate may read a MySQL/MariaDB TIMESTAMP in
        // different zones, hours apart.
        lastActiveAgo(Duration.ofDays(1), waiting, forgotten);

        assertTrue(jobDataService.touchWaitingQueuedRun(waiting.jobQueuedRunId()));
        jobDataService.expireStaleQueuedRuns(
                Map.of(TEST_JOB_ID, Instant.now().minus(Duration.ofMinutes(30))));

        assertEquals(JobRunQueueState.QUEUED, stateOf(waiting));
        assertEquals(JobRunQueueState.CANCELLED, stateOf(forgotten));
    }

    @Test
    void onlyAStillQueuedRowIsTouched() {
        final var claimed = queueOne();
        jobDataService.claimQueuedRun(claimed, "runner-a");

        assertFalse(jobDataService.touchWaitingQueuedRun(claimed.jobQueuedRunId()));
    }

    private void lastActiveAgo(final Duration age, final JobQueuedRun... runs) {
        for (final var run : runs) {
            jobJdbcTemplate.update(
                    "UPDATE t_job_run_queue SET updated_at = ? WHERE pk_job_run_queue_id = ?",
                    Timestamp.from(Instant.now().minus(age)),
                    run.jobQueuedRunId());
        }
    }

    private JobRunQueueState stateOf(final JobQueuedRun run) {
        return allQueueRowsForJob().stream()
                .filter(row -> row.jobQueuedRunId() == run.jobQueuedRunId())
                .findFirst()
                .orElseThrow()
                .queueState();
    }

    private JobQueuedRun queueHandoffContinuation() {
        queueClaimAndRunToRunning();
        return jobDataService.queueReprocessingRun(
                TEST_JOB_ID,
                "1.0.0",
                new JobRunArguments(),
                Set.of(),
                EXISTING_JOB_RUN_ID,
                JobRunnerKey.DEDICATED_K8S_JOB);
    }

    private JobQueuedRun queueOne() {
        return jobDataService.queueNewRun(
                TEST_JOB_ID,
                null,
                "1.0.0",
                new JobRunArguments(),
                Set.of(),
                JobQueueBehavior.ALLOW_MULTIPLE);
    }

    private int queueClaimAndRunToRunning() {
        final var run = queueOne();
        jobDataService.claimQueuedRun(run, "runner-a");
        jobDataService.setRunId(run, EXISTING_JOB_RUN_ID);
        return run.jobQueuedRunId();
    }

    /** Every queue row for {@link #TEST_JOB_ID}, regardless of state. */
    private List<JobQueuedRun> allQueueRowsForJob() {
        return jobDataService
                .findQueuedRuns(
                        PageRequest.of(0, 100),
                        List.of(TEST_JOB_ID),
                        List.of(JobRunQueueState.values()),
                        Optional.empty())
                .getContent();
    }

    private Optional<JobQueuedRun> findInQueue(final int jobQueuedRunId) {
        return Stream.concat(
                        jobDataService
                                .findQueuedRunsPage(PageRequest.of(0, 100))
                                .getContent()
                                .stream(),
                        jobDataService.findQueuedRunsByState(JobRunQueueState.RUNNING).stream())
                .filter(r -> r.jobQueuedRunId() == jobQueuedRunId)
                .findFirst();
    }
}
