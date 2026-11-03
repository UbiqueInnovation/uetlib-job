package ch.ubique.uetlib.job.service.triggers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import ch.ubique.uetlib.job.cleanup.CleanupJob;
import ch.ubique.uetlib.job.data.service.JobDataService;
import ch.ubique.uetlib.job.model.JobClassAndId;
import ch.ubique.uetlib.job.model.JobRun;
import ch.ubique.uetlib.job.model.JobRunArguments;
import ch.ubique.uetlib.job.model.JobRunState;
import ch.ubique.uetlib.job.model.JobRunStateTransition;
import ch.ubique.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.ubique.uetlib.job.model.triggers.JobRunStateTrigger;
import ch.ubique.uetlib.job.model.triggers.TriggerType;
import ch.ubique.uetlib.job.service.JobService;
import ch.ubique.uetlib.job.service.core.ClassLoaderService;
import ch.ubique.uetlib.job.storage.JobRunStorage;
import ch.ubique.uetlib.job.test.config.JobRunStateTriggerServiceTestConfig;

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
    void testJobRunStateTriggers() {
        doReturn(new JobClassAndId(0, CleanupJob.class.getCanonicalName()))
                .when(jobService)
                .findJobByIdOrThrow(anyInt());

        final var triggerService = new JobRunStateTriggerService(jobDataService, jobService);

        final var doneRunCount = new AtomicInteger();
        final var scheduledRunCount = new AtomicInteger();

        triggerService.addTrigger(
                1,
                new JobRunStateTrigger(CleanupJob.class, JobRunState.DONE),
                null,
                jobRunArguments -> doneRunCount.incrementAndGet());

        triggerService.addTrigger(
                2,
                new JobRunStateTrigger(CleanupJob.class, JobRunState.SCHEDULED),
                null,
                jobRunArguments -> scheduledRunCount.incrementAndGet());

        final var jobRunStartedAt = Instant.now();
        final var jobRun =
                new JobRun(
                        1,
                        1,
                        "1.0.0",
                        jobRunStartedAt,
                        null,
                        JobRunState.SCHEDULED,
                        JobRunnerKey.CURRENT,
                        TriggerType.JOB_RUN_STATE,
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
