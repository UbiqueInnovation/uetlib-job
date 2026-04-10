package ch.ubique.backend.job.logging;

import org.jspecify.annotations.NonNull;

import java.time.Instant;

public record LoggingEvent(@NonNull Instant timestamp, @NonNull String formattedMessage) {}
