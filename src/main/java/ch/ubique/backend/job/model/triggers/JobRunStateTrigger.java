package ch.ubique.backend.job.model.triggers;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRunState;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

public record JobRunStateTrigger(
        @JsonProperty(required = true) @NotNull Class<? extends Job> jobClass,
        @JsonProperty(required = true) @NotNull JobRunState state)
        implements TriggerDefinition {}
