package ch.ubique.backend.job.data.service;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.job.data.BaseJobDatabaseTest;
import ch.ubique.backend.job.model.JobQueuedRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.service.inputs.JobRunInputManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
}
