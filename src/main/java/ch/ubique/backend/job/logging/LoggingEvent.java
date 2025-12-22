package ch.ubique.backend.job.logging;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record LoggingEvent(@NotNull Instant timestamp, @NotNull String formattedMessage) {}