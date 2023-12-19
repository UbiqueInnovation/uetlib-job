package ch.ubique.backend.job.data.service.runs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.service.controller.JobControllerService;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Sql("classpath:sql/postgresql/InsertJobs.sql")
class JobRunFilterTest {
    private JobControllerService jobControllerService;

    @Test
    void testWithoutParams() throws ClassNotFoundException {
        final var jobRunsOld =
                jobControllerService.findJobRuns(
                        0, 50, null, List.of(), List.of(), null, null, false);
        final var jobRunsNew =
                jobControllerService.findJobRuns(
                        0, 50, null, List.of(), List.of(), null, null, true);

        assertEquals(jobRunsOld, jobRunsNew);
    }

    @Test
    void testWithState() throws ClassNotFoundException {
        final var jobRunsOld =
                jobControllerService.findJobRuns(
                        0, 50, JobRunState.DONE, List.of(), List.of(), null, null, false);
        final var jobRunsNew =
                jobControllerService.findJobRuns(
                        0, 50, JobRunState.DONE, List.of(), List.of(), null, null, true);

        assertEquals(jobRunsOld, jobRunsNew);
    }

    @Test
    void testWithIncludeId() throws ClassNotFoundException {
        final var jobRunsOld =
                jobControllerService.findJobRuns(
                        0, 50, null, List.of(String.valueOf(1)), List.of(), null, null, false);
        final var jobRunsNew =
                jobControllerService.findJobRuns(
                        0, 50, null, List.of(String.valueOf(1)), List.of(), null, null, true);

        assertEquals(jobRunsOld, jobRunsNew);
    }

    @Test
    void testWithExcludeId() throws ClassNotFoundException {
        final var jobRunsOld =
                jobControllerService.findJobRuns(
                        0, 50, null, List.of(), List.of(String.valueOf(1)), null, null, false);
        final var jobRunsNew =
                jobControllerService.findJobRuns(
                        0, 50, null, List.of(), List.of(String.valueOf(1)), null, null, true);

        assertEquals(jobRunsOld, jobRunsNew);
    }

    @Test
    void testWithDateTimeRange() throws ClassNotFoundException {
        final var instantFrom = Instant.ofEpochMilli(1702980000000L); // 2023-12-19T10:00:00Z
        final var instantTo = Instant.ofEpochMilli(1702998000000L); // 2023-12-19T15:00:00Z
        final var jobRunsOld =
                jobControllerService.findJobRuns(
                        0,
                        50,
                        null,
                        List.of(),
                        List.of(),
                        LocalDateTime.ofInstant(instantFrom, ZoneOffset.UTC),
                        LocalDateTime.ofInstant(instantTo, ZoneOffset.UTC),
                        false);
        final var jobRunsNew =
                jobControllerService.findJobRuns(
                        0,
                        50,
                        null,
                        List.of(),
                        List.of(),
                        LocalDateTime.ofInstant(instantFrom, ZoneOffset.UTC),
                        LocalDateTime.ofInstant(instantTo, ZoneOffset.UTC),
                        true);

        assertEquals(jobRunsOld, jobRunsNew);
    }
}
