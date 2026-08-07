package ch.ubique.backend.job.data.service;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.job.data.BaseJobDatabaseTest;
import ch.ubique.backend.job.model.JobQueuedRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.queue.JobRunQueueState;
import ch.ubique.backend.job.service.inputs.JobRunInputManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Exercises {@code t_job_run_queue}'s ordered/paged listing query ({@link
 * ch.ubique.backend.job.data.repository.JobQueuedRunRepository#findByQueueStateOrderedByPriority}),
 * which {@link ch.ubique.backend.job.service.executor.JobRunQueueService} pages through and filters
 * by taint in Java - taint matching itself has no SQL counterpart anymore and is covered by a plain
 * unit test instead ({@code JobRunQueueServiceTest}).
 */
class JobQueuedRunDatabaseTest extends BaseJobDatabaseTest {

    private static final int TEST_JOB_ID = 1;

    @Autowired JobDataService jobDataService;
    @MockitoBean JobRunInputManager jobRunInputManager;

    @Test
    void onlyQueuedStateRunsAreReturned() {
        final var stillQueued =
                jobDataService.queueNewRun(
                        TEST_JOB_ID, null, "1.0.0", new JobRunArguments(), Set.of(), true);
        final var claimed =
                jobDataService.queueNewRun(
                        TEST_JOB_ID, null, "1.0.0", new JobRunArguments(), Set.of(), true);
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
                        TEST_JOB_ID, 1, "1.0.0", new JobRunArguments(), Set.of(), true);
        final var high =
                jobDataService.queueNewRun(
                        TEST_JOB_ID, 5, "1.0.0", new JobRunArguments(), Set.of(), true);

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
                                                true))
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
    void deleteQueuedRunIfNotRunningDeletesAQueuedRow() {
        final var run = queueOne();

        assertTrue(jobDataService.deleteQueuedRunIfNotRunning(run.jobQueuedRunId()));
        assertTrue(findInQueue(run.jobQueuedRunId()).isEmpty());
    }

    @Test
    void deleteQueuedRunIfNotRunningNeverDeletesARunningRow() {
        final var runningRunId = queueClaimAndRunToRunning();

        assertFalse(jobDataService.deleteQueuedRunIfNotRunning(runningRunId));
        assertEquals(
                1,
                jobDataService.findQueuedRunsByState(JobRunQueueState.RUNNING).stream()
                        .filter(r -> r.jobQueuedRunId() == runningRunId)
                        .count());
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
                TEST_JOB_ID, null, "1.0.0", new JobRunArguments(), Set.of(), false);

        assertTrue(
                findInQueue(runningRunId).isPresent(),
                "a job's trigger firing again must not delete its already-RUNNING queue row");
    }

    @Test
    void queueNewRunDeletesAnExistingQueuedRowWhenDedupingToTheLatest() {
        final var previouslyQueued = queueOne();

        jobDataService.queueNewRun(
                TEST_JOB_ID, null, "1.0.0", new JobRunArguments(), Set.of(), false);

        assertTrue(findInQueue(previouslyQueued.jobQueuedRunId()).isEmpty());
    }

    @Test
    void resetStaleClaimsResetsClaimsOlderThanTheGivenThreshold() {
        final var run = queueOne();
        jobDataService.claimQueuedRun(run, "runner-a");

        // The heartbeat was just set to Instant.now() by claimQueuedRun, so a staleBefore in the
        // future makes it "stale" without needing to fabricate an old heartbeat timestamp.
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
    void queueRunningRunIsIdempotentForAJobRunIdAlreadyRunning() {
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
    }

    /** An existing {@code t_job_run} row from the fixtures, valid as {@code fk_job_run_id}. */
    private static final int EXISTING_JOB_RUN_ID = 2;

    private JobQueuedRun queueOne() {
        return jobDataService.queueNewRun(
                TEST_JOB_ID, null, "1.0.0", new JobRunArguments(), Set.of(), true);
    }

    private int queueClaimAndRunToRunning() {
        final var run = queueOne();
        jobDataService.claimQueuedRun(run, "runner-a");
        jobDataService.setRunId(run, EXISTING_JOB_RUN_ID);
        return run.jobQueuedRunId();
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
