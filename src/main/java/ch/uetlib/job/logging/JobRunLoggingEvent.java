package ch.uetlib.job.logging;

import java.time.Instant;

public record JobRunLoggingEvent(int jobRunId, Instant timestamp, String formattedMessage) {}
