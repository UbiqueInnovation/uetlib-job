package ch.ubique.backend.job.service.triggers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.ubique.backend.job.cleanup.CleanupJob;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.triggers.JobRunStateTrigger;
import ch.ubique.backend.job.service.JobService;

import org.junit.jupiter.api.Test;
import org.mockito.internal.stubbing.answers.Returns;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

class JobRunStateTriggerServiceTest {

    @Test
    void test() throws ClassNotFoundException {
        final var jobDataService = mock(JobDataService.class);
        final var jobService = mock(JobService.class);

        when(jobService.getJobClass(anyInt())).then(new Returns(CleanupJob.class));

        final var triggerService = new JobRunStateTriggerService(jobDataService, jobService);

        final var doneRunCount = new AtomicInteger();
        final var scheduledRunCount = new AtomicInteger();

        triggerService.addTrigger(
                1,
                new JobRunStateTrigger(CleanupJob.class, JobRunState.DONE),
                doneRunCount::incrementAndGet);

        triggerService.addTrigger(
                2,
                new JobRunStateTrigger(CleanupJob.class, JobRunState.SCHEDULED),
                scheduledRunCount::incrementAndGet);

        final var jobRun = new JobRun(1, 1, 1, Instant.now(), null, JobRunState.SCHEDULED);
        when(jobDataService.findJobRunsWithoutFinishedTimestamp()).thenReturn(Set.of(jobRun));

        triggerService.checkJobRuns();

        assertEquals(1, scheduledRunCount.get());
        assertEquals(0, doneRunCount.get());

        when(jobDataService.findJobRunsWithoutFinishedTimestamp()).thenReturn(Set.of());
        when(jobDataService.findJobRunsFinishedAfter(any()))
                .thenReturn(
                        Set.of(
                                jobRun.modify()
                                        .state(JobRunState.DONE)
                                        .finishedAt(Instant.now())
                                        .toJobRun()));
        triggerService.checkJobRuns();

        assertEquals(1, scheduledRunCount.get());
        assertEquals(1, doneRunCount.get());
    }
}
