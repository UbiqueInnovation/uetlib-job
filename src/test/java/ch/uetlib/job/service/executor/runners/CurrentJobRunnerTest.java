package ch.uetlib.job.service.executor.runners;

import static ch.uetlib.job.model.properties.JobPropertyDefinitions.RUNNER_GROUP;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.uetlib.job.Job;
import ch.uetlib.job.cleanup.CleanupJob;
import ch.uetlib.job.cleanup.JobRunInputCleaner;
import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.health.HealthCheckJob;
import ch.uetlib.job.model.*;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.runner.RunnerGroup;
import ch.uetlib.job.service.JobService;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class CurrentJobRunnerTest {

    private final JobService jobService = mock(JobService.class);
    private final CurrentJobRunner runner = new CurrentJobRunner(jobService);

    {
        // Stubbed once, up front: the runs below call it from the executor's threads.
        when(jobService.startJobRun(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void key() {
        assertEquals(JobRunnerKey.CURRENT, new CurrentJobRunner(mock(JobService.class)).key());
    }

    @Test
    void submit() {
        final var currentRunnerWithMock = new CurrentJobRunner(mock(JobService.class));
        final var jobProperties = mock(JobProperties.class);
        when(jobProperties.get(RUNNER_GROUP))
                .thenReturn(RUNNER_GROUP.createProperty(HealthCheckJob.runnerGroup()));
        final var jobRunContext =
                new JobRunContext(
                        mock(JobClassAndId.class),
                        mock(JobRun.class),
                        mock(JobRunArguments.class),
                        jobProperties);
        assertTrue(currentRunnerWithMock.submit(new HealthCheckJob(), jobRunContext));
        assertFalse(
                currentRunnerWithMock.submit(
                        new CleanupJob(
                                mock(JobRunInputCleaner.class),
                                mock(JobService.class),
                                mock(JobDataService.class)),
                        jobRunContext),
                "Only the HealthCheckJob can be submitted to the HealthCheckJob.runnerGroup().");
    }

    @Test
    void aRunnerGroupThatNeverRanHasCapacity() {
        assertTrue(runner.hasCapacity(new RunnerGroup("capacity-fresh", 1, null)));
    }

    @Test
    void aGroupOfTwoStillHasCapacityWhileOneRunIsActive() throws InterruptedException {
        // The executor creates its threads only as runs arrive, so its pool size is no measure of
        // what is free.
        final var group = new RunnerGroup("capacity-pair", 2, null);
        final var release = new CountDownLatch(1);
        try {
            final var started = new CountDownLatch(1);
            runner.submit(blockingJob(started, release), context(group, 1));
            assertTrue(started.await(5, TimeUnit.SECONDS));

            assertTrue(runner.hasCapacity(group));

            runner.submit(blockingJob(new CountDownLatch(1), release), context(group, 2));
            assertFalse(runner.hasCapacity(group));
        } finally {
            release.countDown();
        }
    }

    @Test
    void aSubmittedRunTakesItsThreadBeforeAnyThreadPicksItUp() {
        final var group = new RunnerGroup("capacity-single-taken", 1, null);
        final var release = new CountDownLatch(1);
        try {
            runner.submit(blockingJob(new CountDownLatch(1), release), context(group, 1));

            assertFalse(runner.hasCapacity(group));
        } finally {
            release.countDown();
        }
    }

    @Test
    void aFinishedRunFreesItsThread() {
        final var group = new RunnerGroup("capacity-single-freed", 1, null);
        final var release = new CountDownLatch(1);
        runner.submit(blockingJob(new CountDownLatch(1), release), context(group, 1));

        release.countDown();

        await().atMost(Duration.ofSeconds(5)).until(() -> runner.hasCapacity(group));
        assertTrue(runner.hasCapacity(group));
    }

    /** Counts {@code started} down once running, then holds its thread until {@code release}. */
    private static Job blockingJob(final CountDownLatch started, final CountDownLatch release) {
        return _ -> {
            started.countDown();
            release.await();
            return JobRunState.DONE;
        };
    }

    private static JobRunContext context(final RunnerGroup group, final int jobRunId) {
        final var properties = mock(JobProperties.class);
        when(properties.get(RUNNER_GROUP)).thenReturn(RUNNER_GROUP.createProperty(group));
        return new JobRunContext(
                mock(JobClassAndId.class),
                new JobRun(
                        jobRunId,
                        1,
                        "1.0.0",
                        Instant.now(),
                        null,
                        JobRunState.SCHEDULED,
                        JobRunnerKey.CURRENT,
                        null,
                        null),
                new JobRunArguments(),
                properties);
    }
}
