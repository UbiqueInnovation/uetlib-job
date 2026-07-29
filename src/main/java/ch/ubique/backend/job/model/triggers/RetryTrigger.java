package ch.ubique.backend.job.model.triggers;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

import java.time.Duration;

public record RetryTrigger(
        @JsonProperty(required = true) int maxRetries,
        @JsonProperty(required = true) @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING)
                Duration constantBackoff)
        implements TriggerDefinition {}
