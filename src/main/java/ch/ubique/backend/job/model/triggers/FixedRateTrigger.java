package ch.ubique.backend.job.model.triggers;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record FixedRateTrigger(
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) Duration period,
        @Nullable @JsonFormat(shape = JsonFormat.Shape.STRING) Duration initialDelay)
        implements TriggerDefinition {
    public FixedRateTrigger(@NotBlank final Duration period) {
        this(period, null);
    }
}
