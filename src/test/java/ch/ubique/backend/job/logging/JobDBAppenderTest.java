package ch.ubique.backend.job.logging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.service.logs.LogFormatterService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

class JobDBAppenderTest {

    private JobDataService jobDataService;
    private LogFormatterService logFormatterService;
    private JobDBAppender appender;

    @BeforeEach
    void setUp() {
        jobDataService = mock(JobDataService.class);
        logFormatterService = mock(LogFormatterService.class);
        appender = new JobDBAppender(jobDataService, logFormatterService, null);
    }

    @Test
    void testAppendPutsToQueue() {
        final var event = mock(ILoggingEvent.class);
        when(event.getMDCPropertyMap()).thenReturn(Map.of("jobRunId", "123"));
        when(event.getInstant()).thenReturn(Instant.now());
        when(logFormatterService.toFormattedMessage(event)).thenReturn("formatted msg");

        appender.append(event);

        // Should not be inserted immediately
        verify(jobDataService, never()).insertLogEvents(anyList());

        // Stop appender to flush
        appender.stop();

        verify(jobDataService, times(1)).insertLogEvents(anyCollection());
    }

    @Test
    void scheduledFlushContinuesAfterUnexpectedFailure() throws InterruptedException {
        // Reusing an exception can happen when a dependency consistently reports the same failure.
        // The individual-insert fallback then throws while attempting to suppress it onto itself.
        final var failure = new IllegalStateException("insert failed");
        doThrow(new DataIntegrityViolationException("batch rejected"))
                .doThrow(failure)
                .doThrow(failure)
                .doNothing()
                .when(jobDataService)
                .insertLogEvents(anyCollection());

        final var errors = new LinkedBlockingQueue<ILoggingEvent>();
        final var logger = (Logger) LoggerFactory.getLogger(JobDBAppender.class);
        @SuppressWarnings("unchecked")
        final Appender<ILoggingEvent> logCapture = mock(Appender.class);
        doAnswer(
                        invocation -> {
                            final ILoggingEvent event = invocation.getArgument(0);
                            if (event.getFormattedMessage()
                                    .startsWith("Unexpected failure flushing")) {
                                event.prepareForDeferredProcessing();
                                errors.add(event);
                            }
                            return null;
                        })
                .when(logCapture)
                .doAppend(any());
        logger.addAppender(logCapture);
        try {
            final var event = mock(ILoggingEvent.class);
            when(event.getMDCPropertyMap()).thenReturn(Map.of("jobRunId", "123"));
            when(event.getInstant()).thenReturn(Instant.now());
            when(logFormatterService.toFormattedMessage(event)).thenReturn("message");
            appender.append(event);
            appender.append(event);
            appender.start();

            final var loggedFailure = errors.poll(5, TimeUnit.SECONDS);
            assertNotNull(loggedFailure, "Unexpected flush failure must be logged");
            assertEquals(
                    IllegalArgumentException.class.getName(),
                    loggedFailure.getThrowableProxy().getClassName());

            appender.append(event);
            // Verify before stop(), which performs a separate synchronous flush.
            verify(jobDataService, timeout(7000).times(4)).insertLogEvents(anyCollection());
        } finally {
            appender.stop();
            logger.detachAppender(logCapture);
        }
    }

    @Test
    void testBulkInsert() {
        final var event1 = mock(ILoggingEvent.class);
        when(event1.getMDCPropertyMap()).thenReturn(Map.of("jobRunId", "123"));
        when(event1.getInstant()).thenReturn(Instant.now());
        when(logFormatterService.toFormattedMessage(event1)).thenReturn("msg1");

        final var event2 = mock(ILoggingEvent.class);
        when(event2.getMDCPropertyMap()).thenReturn(Map.of("jobRunId", "123"));
        when(event2.getInstant()).thenReturn(Instant.now());
        when(logFormatterService.toFormattedMessage(event2)).thenReturn("msg2");

        appender.append(event1);
        appender.append(event2);

        appender.stop();

        //noinspection unchecked
        final ArgumentCaptor<Collection<JobRunLoggingEvent>> captor =
                ArgumentCaptor.forClass(Collection.class);
        verify(jobDataService).insertLogEvents(captor.capture());

        final var captured = captor.getValue();
        assertEquals(2, captured.size());
    }
}
