package ch.ubique.backend.job.data.service;

import static ch.ubique.backend.core.utils.TimeUtils.isBetween;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.job.data.BaseJobDatabaseTest;
import ch.ubique.backend.job.logging.JobRunLoggingEvent;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.JobRunStateTransition;
import ch.ubique.backend.job.service.inputs.JobRunInputManager;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

@SuppressWarnings("DataFlowIssue")
class JobDataServiceDatabaseTest extends BaseJobDatabaseTest {

    @Autowired JobDataService jobDataService;
    @MockitoBean JobRunInputManager jobRunInputManager;

    @Test
    void testDeleteLogsForJobRunId() {
        assertDoesNotThrow(() -> jobDataService.deleteLogsForJobRunId(42));
    }

    @Test
    void testFindLogsForJobRun() {
        assertEquals(0, jobDataService.findLogsForJobRun(42, 0).size());
    }

    @Test
    void testFindAnyJobRunInStatesSince() {
        final var jobRun =
                jobDataService.findAnyJobRunInStatesSince(
                        1, Set.of(JobRunState.ERROR), Instant.now().minus(Duration.ofHours(6)));
        assertTrue(jobRun.isPresent());

        final var jobRun2 =
                jobDataService.findAnyJobRunInStatesSince(
                        1, Set.of(JobRunState.ERROR), Instant.now().minus(Duration.ofHours(5)));
        assertTrue(jobRun2.isEmpty());

        final var jobRun3 =
                jobDataService.findAnyJobRunInStatesSince(
                        1, Set.of(), Instant.now().minus(Duration.ofHours(24)));
        assertTrue(jobRun3.isEmpty());
    }

    @Test
    void testInsertLogEvents() {
        final var jobRunId = 1;
        final var now = Instant.now();
        final var logs =
                List.of(
                        new JobRunLoggingEvent(jobRunId, now, "msg1"),
                        new JobRunLoggingEvent(jobRunId, now.plusMillis(1), "msg2"));
        jobDataService.insertLogEvents(logs);

        final var insertedLogs = jobDataService.findAllLogsForJobRun(jobRunId);
        assertTrue(insertedLogs.stream().anyMatch(l -> "msg1".equals(l.formattedMessage())));
        assertTrue(insertedLogs.stream().anyMatch(l -> "msg2".equals(l.formattedMessage())));
    }

    @Test
    void testLatestState() {
        final var now = ZonedDateTime.now();

        final var job1Run1Scheduled =
                jobDataService.findLatestJobRunByState(1, JobRunState.SCHEDULED).orElseThrow();
        assertEquals(1, job1Run1Scheduled.id());
        assertEquals(1, job1Run1Scheduled.jobId());
        assertEquals(JobRunState.SCHEDULED, job1Run1Scheduled.latestState());
        assertTrue(
                isBetween(
                        job1Run1Scheduled.startedAt(),
                        now.minusHours(9).toInstant(),
                        now.minusHours(7).toInstant()));
        assertNull(job1Run1Scheduled.finishedAt());
        assertEquals(List.of(JobRunState.SCHEDULED), getJobRunStates(job1Run1Scheduled));

        final var job1Run2Busy =
                jobDataService.findLatestJobRunByState(1, JobRunState.BUSY).orElseThrow();
        assertEquals(2, job1Run2Busy.id());
        assertEquals(1, job1Run2Busy.jobId());
        assertEquals(JobRunState.BUSY, job1Run2Busy.latestState());
        assertTrue(
                isBetween(
                        job1Run2Busy.startedAt(),
                        now.minusHours(8).toInstant(),
                        now.minusHours(6).toInstant()));
        assertNull(job1Run2Busy.finishedAt());
        assertEquals(
                List.of(JobRunState.SCHEDULED, JobRunState.BUSY), getJobRunStates(job1Run2Busy));

        final var job1Run3Done =
                jobDataService.findLatestJobRunByState(1, JobRunState.DONE).orElseThrow();
        assertEquals(3, job1Run3Done.id());
        assertEquals(1, job1Run3Done.jobId());
        assertEquals(JobRunState.DONE, job1Run3Done.latestState());
        assertTrue(
                isBetween(
                        job1Run3Done.startedAt(),
                        now.minusHours(7).toInstant(),
                        now.minusHours(5).toInstant()));
        assertTrue(
                isBetween(
                        job1Run3Done.finishedAt(),
                        now.minusHours(4).toInstant(),
                        now.minusHours(2).toInstant()));
        assertEquals(
                List.of(JobRunState.SCHEDULED, JobRunState.BUSY, JobRunState.DONE),
                getJobRunStates(job1Run3Done));

        final var job1Run4Error =
                jobDataService.findLatestJobRunByState(1, JobRunState.ERROR).orElseThrow();
        assertEquals(4, job1Run4Error.id());
        assertEquals(1, job1Run4Error.jobId());
        assertEquals(JobRunState.ERROR, job1Run4Error.latestState());
        assertTrue(
                isBetween(
                        job1Run4Error.startedAt(),
                        now.minusHours(6).toInstant(),
                        now.minusHours(4).toInstant()));
        assertTrue(
                isBetween(
                        job1Run4Error.finishedAt(),
                        now.minusHours(3).toInstant(),
                        now.minusHours(1).toInstant()));
        assertEquals(
                List.of(JobRunState.SCHEDULED, JobRunState.BUSY, JobRunState.ERROR),
                getJobRunStates(job1Run4Error));

        assertTrue(jobDataService.findLatestJobRunByState(1, JobRunState.SKIPPED).isEmpty());
    }

    private @NonNull List<JobRunState> getJobRunStates(final JobRun jobRun) {
        return jobDataService.findJobRunStateTransitionsByJobRunId(jobRun.id()).stream()
                .map(JobRunStateTransition::state)
                .toList();
    }
}
