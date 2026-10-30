package ch.ubique.uetlib.job.model.triggers;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record Trigger(
        @JsonProperty(required = true) @Positive int id,
        @JsonProperty(required = true) @Positive int jobId,
        @JsonProperty(required = true) @NotNull TriggerDefinition definition) {}
