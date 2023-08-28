package ch.ubique.backend.job.model.triggers;

import jakarta.validation.constraints.NotBlank;

import java.time.Duration;

public record FixedRateTrigger(@NotBlank Duration period, Duration initialDelay)
        implements TriggerDefinition {
    public FixedRateTrigger(@NotBlank final Duration period) {
        this(period, null);
    }
}
