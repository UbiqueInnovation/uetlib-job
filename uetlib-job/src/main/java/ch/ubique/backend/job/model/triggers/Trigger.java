package ch.ubique.backend.job.model.triggers;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record Trigger(
        @Positive int id,
        @Positive int jobId,
        @NotNull TriggerDefinition definition) {}
