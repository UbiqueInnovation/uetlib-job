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
import ch.ubique.backend.job.model.JobRunStateTransition;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.triggers.JobRunStateTrigger;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.core.ClassLoaderService;
import ch.ubique.backend.job.storage.JobRunStorage;
import ch.ubique.backend.job.test.config.JobRunStateTriggerServiceTestConfig;

import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(classes = JobRunStateTriggerServiceTestConfig.class)
class JobRunStateTriggerServiceTest {

    @MockitoBean private JobDataService jobDataService;
    @MockitoBean private ClassLoaderService classLoaderService;
    @MockitoBean private BuildProperties buildProperties;
    @MockitoBean private Environment environment;
    @MockitoBean private JobRunStorage jobRunStorage;
    @MockitoBean private JobService jobService;

    @Test
    void testJobRunStateTriggers() throws ClassNotFoundException {
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

        final var jobRunStartedAt = Instant.now();
        final var jobRun =
                new JobRun(
                        1,
                        1,
                        1,
                        "1.0.0",
                        jobRunStartedAt,
                        null,
                        JobRunState.SCHEDULED,
                        JobRunnerKey.CURRENT,
                        new JobRunArguments());

        final var jobRunScheduledStateTransition =
                new JobRunStateTransition(1, jobRun, JobRunState.SCHEDULED, jobRunStartedAt);
        doReturn(List.of(jobRunScheduledStateTransition))
                .when(jobDataService)
                .findJobRunStateTransitionsSince(any());

        triggerService.checkJobRuns();

        assertEquals(1, scheduledRunCount.get());
        assertEquals(0, doneRunCount.get());

        final var jobRunFinishedAt = Instant.now();
        final var jobRunDoneStateTransition =
                new JobRunStateTransition(
                        2,
                        jobRun.modify()
                                .state(JobRunState.DONE)
                                .finishedAt(jobRunFinishedAt)
                                .toJobRun(),
                        JobRunState.DONE,
                        jobRunFinishedAt);
        doReturn(List.of(jobRunDoneStateTransition))
                .when(jobDataService)
                .findJobRunStateTransitionsSince(any());

        triggerService.checkJobRuns();

        assertEquals(1, scheduledRunCount.get());
        assertEquals(1, doneRunCount.get());
    }
}
