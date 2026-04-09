package ch.ubique.backend.job.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.service.logs.LogFormatterService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

class JobDBAppenderTest {

    private JobDataService jobDataService;
    private LogFormatterService logFormatterService;
    private JobDBAppender appender;

    @BeforeEach
    void setUp() {
        jobDataService = mock(JobDataService.class);
        logFormatterService = mock(LogFormatterService.class);
        appender = new JobDBAppender(jobDataService, logFormatterService, null);
        appender.start();
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
