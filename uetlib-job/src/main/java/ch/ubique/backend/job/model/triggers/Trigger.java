package ch.ubique.backend.job.model.triggers;

import jakarta.validation.constraints.NotNull;

public record Trigger(int id, int jobId, @NotNull TriggerDefinition definition) {}
