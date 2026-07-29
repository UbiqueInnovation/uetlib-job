package ch.ubique.backend.job.model.triggers;

import ch.ubique.backend.job.Job;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

public record DatabaseSubmissionTrigger(
        @JsonProperty(required = true) @NotNull Class<? extends Job> jobClass)
        implements TriggerDefinition {}
