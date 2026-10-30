package ch.ubique.uetlib.job.model.triggers;

import ch.ubique.uetlib.job.Job;
import ch.ubique.uetlib.job.model.JobRunState;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record JobRunStateTrigger(
        @JsonProperty(required = true) @NotNull Class<? extends Job> jobClass,
        @JsonProperty(required = true) @NotNull JobRunState state)
        implements TriggerDefinition {}
