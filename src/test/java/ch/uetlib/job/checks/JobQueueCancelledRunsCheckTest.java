package ch.uetlib.job.checks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.service.JobService;
import ch.uetlib.monitoring.check.models.CheckFailed;
import ch.uetlib.monitoring.check.models.CheckLevel;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class JobQueueCancelledRunsCheckTest {

    private final JobDataService jobDataService = mock(JobDataService.class);
    private final JobService jobService = mock(JobService.class);
    private final JobQueueCancelledRunsCheck check =
            new JobQueueCancelledRunsCheck(CheckLevel.WARNING, jobDataService, jobService);

    @Test
    void passesWhenNothingIsCancelled() {
        when(jobDataService.findQueuedRunsByState(JobRunQueueState.CANCELLED))
                .thenReturn(List.of());

        final var status = check.check();

        assertTrue(status.passed());
    }

    @Test
    void reportsAtTheConfiguredLevel() {
        assertEquals(CheckLevel.WARNING, check.level());
    }

    @Test
    void failsAndReportsTheDistinctAffectedJobsAndCount() {
        final var jobOne = new JobClassAndId(1, "ch.uetlib.job.TestJobOne");
        final var jobTwo = new JobClassAndId(2, "ch.uetlib.job.TestJobTwo");
        when(jobDataService.findQueuedRunsByState(JobRunQueueState.CANCELLED))
                .thenReturn(
                        List.of(
                                cancelledRun(1, jobOne.id()),
                                cancelledRun(2, jobOne.id()),
                                cancelledRun(3, jobTwo.id())));
        when(jobService.findJobById(jobOne.id())).thenReturn(Optional.of(jobOne));
        when(jobService.findJobById(jobTwo.id())).thenReturn(Optional.of(jobTwo));

        final var status = check.check();

        assertFalse(status.passed());
        final var failed = assertInstanceOf(CheckFailed.class, status);
        final var context = assertInstanceOf(Map.class, failed.context());
        assertEquals(3, context.get("count"));
        assertEquals(
                List.of(jobOne.canonicalClassName(), jobTwo.canonicalClassName()),
                context.get("jobs"));
        assertEquals(Map.of(jobOne.name(), 2L, jobTwo.name(), 1L), context.get("jobsCount"));
    }

    private static JobQueuedRun cancelledRun(final int id, final int jobId) {
        final var now = Instant.now();
        return new JobQueuedRun(
                id,
                null,
                jobId,
                "1.0.0",
                JobRunQueueState.CANCELLED,
                null,
                null,
                null,
                now,
                now,
                Set.of(),
                new JobRunArguments());
    }
}
