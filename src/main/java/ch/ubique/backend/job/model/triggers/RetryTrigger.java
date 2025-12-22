package ch.ubique.backend.job.model.triggers;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotNull;

import java.time.Duration;

public record RetryTrigger(
        int maxRetries,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) Duration constantBackoff)
        implements TriggerDefinition {}
