package ch.ubique.backend.job.service.watcher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.service.k8s.KubernetesService;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

class KubernetesJobWatcherTest {
    @Test
    void logsUnexpectedFailureWithJobContext() throws InterruptedException {
        final var kubernetesService = mock(KubernetesService.class);
        final var failure = new IllegalStateException("client unavailable");
        when(kubernetesService.apiClient()).thenThrow(failure);
        final var jobRun = mock(JobRun.class);
        when(jobRun.id()).thenReturn(123);
        final var watcher = new KubernetesJobWatcher(kubernetesService, mock(JobDataService.class));
        final var errors = new LinkedBlockingQueue<ILoggingEvent>();
        final var logger = (Logger) LoggerFactory.getLogger(KubernetesJobWatcher.class);
        @SuppressWarnings("unchecked")
        final Appender<ILoggingEvent> logCapture = mock(Appender.class);
        doAnswer(
                        invocation -> {
                            final ILoggingEvent event = invocation.getArgument(0);
                            event.prepareForDeferredProcessing();
                            errors.add(event);
                            return null;
                        })
                .when(logCapture)
                .doAppend(any());
        logger.addAppender(logCapture);
        try {
            watcher.watch("test-job", jobRun);
            final var event = errors.poll(5, TimeUnit.SECONDS);
            assertNotNull(event, "Background failure must be logged");
            assertEquals("Unexpected failure watching job test-job", event.getFormattedMessage());
            assertEquals("123", event.getMDCPropertyMap().get("jobRunId"));
            assertEquals(failure.getMessage(), event.getThrowableProxy().getMessage());
        } finally {
            logger.detachAppender(logCapture);
        }
    }
}
