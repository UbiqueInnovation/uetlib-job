package ch.ubique.backend.job.model.triggers;

import jakarta.validation.constraints.NotBlank;

import java.time.Duration;

public record FixedRateTrigger(@NotBlank Duration period) implements TriggerDefinition {}
