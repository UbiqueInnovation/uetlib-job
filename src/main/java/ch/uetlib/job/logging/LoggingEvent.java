package ch.uetlib.job.logging;

import java.time.Instant;

public record LoggingEvent(Instant timestamp, String formattedMessage) {}
