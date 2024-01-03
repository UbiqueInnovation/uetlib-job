package ch.ubique.backend.job.data.service.runs;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.job.config.BaseTestConfig;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.data.service.JobDataServiceTest;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.client.JobRunInfosContainer;
import ch.ubique.backend.job.service.controller.JobControllerService;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@SpringBootTest
@EnableAutoConfiguration
@ContextConfiguration(loader = AnnotationConfigContextLoader.class, classes = BaseTestConfig.class)
@Sql("classpath:sql/postgresql/InsertJobRuns.sql")
class JobRunFilterTest extends JobDataServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(JobRunFilterTest.class);
    @Autowired private JobControllerService jobControllerService;
    @Autowired private JobDataService jobDataService;

    @Test
    @Order(0)
    void getInfo() {
        logger.info("JobRunFilterTest");
        final var allJobs = jobDataService.findJobs();
        logger.info("All jobs size: {}", allJobs.size());
        logger.info("All jobs: {}", allJobs);
        assertFalse(allJobs.isEmpty());

        final var allJobRuns =
                jobDataService.findJobRuns(0, 100, List.of(), null, Optional.empty()).toList();
        logger.info("All job runs size: {}", allJobRuns.size());
        logger.info("All job runs: {}", allJobRuns);
        assertFalse(allJobRuns.isEmpty());

        final HashMap<Integer, Integer> jobRunsByJobIdCount =
                allJobRuns.stream()
                        .map(JobRun::jobId)
                        .collect(
                                HashMap::new,
                                (m, v) -> m.put(v, m.getOrDefault(v, 0) + 1),
                                HashMap::putAll);
        logger.info("All job runs by job id: {}", jobRunsByJobIdCount);
    }

    @Test
    void assertJobId1Existing() {
        assertTrue(jobDataService.findJobs().stream().anyMatch(job -> job.id() == 1));
    }

    private void assertJobListsEqualAndNotEmpty(
            final JobRunInfosContainer jobRunsOld, final JobRunInfosContainer jobRunsNew) {
        assertNotEquals(0, jobRunsOld.jobRuns().size());
        assertEquals(jobRunsOld.jobRuns().size(), jobRunsNew.jobRuns().size());
    }

    @Test
    void testWithoutParams() throws ClassNotFoundException {
        final var jobRunsNew =
                jobControllerService.findJobRuns(
                        0, 5, null, List.of(), List.of(), null, null, true);
        final var jobRunsOld =
                jobControllerService.findJobRuns(
                        0, 5, null, List.of(), List.of(), null, null, false);

        assertJobListsEqualAndNotEmpty(jobRunsOld, jobRunsNew);
    }

    @Test
    void testWithState() throws ClassNotFoundException {
        final var jobRunsOld =
                jobControllerService.findJobRuns(
                        0, 5, JobRunState.BUSY, List.of(), List.of(), null, null, false);
        final var jobRunsNew =
                jobControllerService.findJobRuns(
                        0, 5, JobRunState.BUSY, List.of(), List.of(), null, null, true);

        assertJobListsEqualAndNotEmpty(jobRunsOld, jobRunsNew);
    }

    @Test
    void testWithIncludeId() throws ClassNotFoundException {
        final JobRunInfosContainer jobRunsOld =
                jobControllerService.findJobRuns(
                        0, 5, null, List.of(String.valueOf(2)), List.of(), null, null, false);
        final JobRunInfosContainer jobRunsNew =
                jobControllerService.findJobRuns(
                        0, 5, null, List.of(String.valueOf(2)), List.of(), null, null, true);

        assertNotNull(jobDataService.findLatestJobRun(1));
        assertJobListsEqualAndNotEmpty(jobRunsOld, jobRunsNew);
    }

    @Test
    void testWithExcludeId() throws ClassNotFoundException {
        final var jobRunsOld =
                jobControllerService.findJobRuns(
                        0, 50, null, List.of(), List.of(String.valueOf(1)), null, null, false);
        final var jobRunsNew =
                jobControllerService.findJobRuns(
                        0, 50, null, List.of(), List.of(String.valueOf(1)), null, null, true);

        assertNotNull(jobDataService.findLatestJobRun(1));
        assertJobListsEqualAndNotEmpty(jobRunsOld, jobRunsNew);
    }

    @Test
    void testWithDateTimeRange() throws ClassNotFoundException {
        final var instantFrom = Instant.ofEpochMilli(1702980000000L); // 2023-12-19T10:00:00Z
        final var instantTo = Instant.ofEpochMilli(1702998000000L); // 2023-12-19T15:00:00Z
        final var jobRunsOld =
                jobControllerService.findJobRuns(
                        0,
                        5,
                        null,
                        List.of(),
                        List.of(),
                        LocalDateTime.ofInstant(instantFrom, ZoneOffset.UTC),
                        LocalDateTime.ofInstant(instantTo, ZoneOffset.UTC),
                        false);
        final var jobRunsNew =
                jobControllerService.findJobRuns(
                        0,
                        5,
                        null,
                        List.of(),
                        List.of(),
                        LocalDateTime.ofInstant(instantFrom, ZoneOffset.UTC),
                        LocalDateTime.ofInstant(instantTo, ZoneOffset.UTC),
                        true);

        assertNotNull(jobDataService.findLatestJobRun(1));
        assertJobListsEqualAndNotEmpty(jobRunsOld, jobRunsNew);
    }

    @Test
    @Disabled(
            "This edge case is currently not handled by returning an empty collection but not excluding any job.")
    void testId3NotExisting() throws ClassNotFoundException {
        final var jobRunsOld =
                jobControllerService.findJobRuns(
                        0, 5, null, List.of("3"), List.of(), null, null, false);
        final var jobRunsNew =
                jobControllerService.findJobRuns(
                        0, 5, null, List.of("3"), List.of(), null, null, true);
        assertEquals(0, jobRunsNew.jobRuns().size());
        assertEquals(0, jobRunsOld.jobRuns().size());
    }
}
