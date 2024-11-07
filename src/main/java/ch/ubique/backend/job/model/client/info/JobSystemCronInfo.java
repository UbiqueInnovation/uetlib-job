package ch.ubique.backend.job.model.client.info;

import jakarta.validation.constraints.NotNull;

import java.time.ZoneId;

public record JobSystemCronInfo(@NotNull ZoneId zoneId) {}
