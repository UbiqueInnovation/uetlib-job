package ch.ubique.backend.job.service.triggers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import ch.ubique.backend.job.cleanup.CleanupJob;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.triggers.JobRunStateTrigger;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.config.JobRunStateTriggerServiceTestConfig;
import ch.ubique.backend.job.service.core.ClassLoaderService;
import ch.ubique.backend.job.storage.JobRunStorage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(classes = JobRunStateTriggerServiceTestConfig.class)
class JobRunStateTriggerServiceTest {

    @MockBean private JobDataService jobDataService;

    @MockBean private ClassLoaderService classLoaderService;

    @MockBean private BuildProperties buildProperties;

    @MockBean private Environment environment;

    @MockBean private JobRunStorage jobRunStorage;

    @SpyBean private JobService jobService;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void test() throws ClassNotFoundException {

        doReturn(CleanupJob.class).when(jobService).getJobClass(anyInt());

        final var triggerService = new JobRunStateTriggerService(jobDataService, jobService);

        final var doneRunCount = new AtomicInteger();
        final var scheduledRunCount = new AtomicInteger();

        triggerService.addTrigger(
                1,
                new JobRunStateTrigger(CleanupJob.class, JobRunState.DONE),
                jobRunArguments -> doneRunCount.incrementAndGet());

        triggerService.addTrigger(
                2,
                new JobRunStateTrigger(CleanupJob.class, JobRunState.SCHEDULED),
                jobRunArguments -> scheduledRunCount.incrementAndGet());

        final var jobRun =
                new JobRun(
                        1,
                        1,
                        1,
                        Instant.now(),
                        null,
                        JobRunState.SCHEDULED,
                        JobRunnerKey.CURRENT,
                        new JobRunArguments());
        doReturn(Set.of(jobRun)).when(jobDataService).findAllCurrentRunnerJobRunsWithoutFinishedTimestamp();

        triggerService.checkJobRuns();

        assertEquals(1, scheduledRunCount.get());
        assertEquals(0, doneRunCount.get());

        doReturn(Set.of()).when(jobDataService).findAllCurrentRunnerJobRunsWithoutFinishedTimestamp();
        doReturn(
                        Set.of(
                                jobRun.modify()
                                        .state(JobRunState.DONE)
                                        .finishedAt(Instant.now())
                                        .toJobRun()))
                .when(jobDataService)
                .findJobRunsFinishedAfter(any());
        triggerService.checkJobRuns();

        assertEquals(1, scheduledRunCount.get());
        assertEquals(1, doneRunCount.get());
    }

    @Test
    void instantiateJob() {
        final var job1 = jobService.instantiateJob(CleanupJob.class);
        final var job2 = jobService.instantiateJob(CleanupJob.class);
        assertNotEquals(job1, job2);
    }
}
