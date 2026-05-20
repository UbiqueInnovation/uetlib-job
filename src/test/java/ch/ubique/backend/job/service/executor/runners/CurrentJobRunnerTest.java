package ch.ubique.backend.job.service.executor.runners;

import static ch.ubique.backend.job.model.properties.JobPropertyDefinitions.RUNNER_GROUP;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.ubique.backend.job.cleanup.CleanupJob;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.health.HealthCheckJob;
import ch.ubique.backend.job.model.*;
import ch.ubique.backend.job.model.properties.JobProperties;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.storage.JobRunStorage;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

class CurrentJobRunnerTest {

    @Test
    void key() {
        assertEquals(JobRunnerKey.CURRENT, new CurrentJobRunner(mock(JobService.class)).key());
    }

    @Test
    void submit() {
        final var runner = new CurrentJobRunner(mock(JobService.class));
        final var jobProperties = mock(JobProperties.class);
        when(jobProperties.get(RUNNER_GROUP))
                .thenReturn(RUNNER_GROUP.createProperty(HealthCheckJob.runnerGroup()));
        final var jobRunContext =
                new JobRunContext(
                        mock(JobClassAndId.class),
                        mock(JobRun.class),
                        mock(JobRunArguments.class),
                        jobProperties);
        assertTrue(runner.submit(new HealthCheckJob(), jobRunContext));
        assertFalse(
                runner.submit(
                        new CleanupJob(
                                mock(JobRunStorage.class),
                                mock(JobService.class),
                                mock(JobDataService.class)),
                        jobRunContext),
                "Only the HealthCheckJob can be submitted to the HealthCheckJob.runnerGroup().");
    }

    @Test
    void resolveJobRunStateLogsOutcomeForFailedFuture() throws InterruptedException {
        final var failure = new RuntimeException("Failed to start job run");
        final var jobRun =
                new JobRun(
                        123,
                        1,
                        "test-version",
                        Instant.now(),
                        null,
                        JobRunState.SCHEDULED,
                        JobRunnerKey.CURRENT,
                        null,
                        JobRunArguments.DEFAULT);
        final var jobService = mock(JobService.class);
        when(jobService.startJobRun(any(JobRun.class), any())).thenThrow(failure);
        final var runner = new CurrentJobRunner(jobService);
        final var jobProperties = mock(JobProperties.class);
        when(jobProperties.get(RUNNER_GROUP))
                .thenReturn(RUNNER_GROUP.createProperty(HealthCheckJob.runnerGroup()));
        final var jobRunContext =
                new JobRunContext(
                        mock(JobClassAndId.class),
                        jobRun,
                        JobRunArguments.DEFAULT,
                        jobProperties);
        final var logAppender = createListAppender();

        try {
            assertTrue(runner.submit(new HealthCheckJob(), jobRunContext));

            assertEquals(
                    JobRunState.ERROR,
                    waitForResolvedState(runner, jobRun, JobRunState.ERROR, Duration.ofSeconds(1)));
        } finally {
            detachListAppender(logAppender);
        }

        final var logEvent =
                logAppender.list.stream()
                        .filter(
                                event ->
                                        event.getLevel() == Level.ERROR
                                                && event.getFormattedMessage()
                                                        .contains("Current job run 123 failed."))
                        .findFirst()
                        .orElseThrow();
        assertInstanceOf(ThrowableProxy.class, logEvent.getThrowableProxy());
        assertSame(failure, ((ThrowableProxy) logEvent.getThrowableProxy()).getThrowable());
    }

    private static JobRunState waitForResolvedState(
            final CurrentJobRunner runner,
            final JobRun jobRun,
            final JobRunState expectedState,
            final Duration timeout)
            throws InterruptedException {
        final var deadline = System.nanoTime() + timeout.toNanos();
        JobRunState resolvedState;
        do {
            resolvedState = runner.resolveJobRunState(jobRun);
            if (resolvedState == expectedState) {
                return resolvedState;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        return resolvedState;
    }

    private static ListAppender<ILoggingEvent> createListAppender() {
        final var logger = (Logger) LoggerFactory.getLogger(CurrentJobRunner.class);
        final var logAppender = new ListAppender<ILoggingEvent>();
        logAppender.start();
        logger.addAppender(logAppender);
        return logAppender;
    }

    private static void detachListAppender(final ListAppender<ILoggingEvent> logAppender) {
        final var logger = (Logger) LoggerFactory.getLogger(CurrentJobRunner.class);
        logger.detachAppender(logAppender);
    }
}
