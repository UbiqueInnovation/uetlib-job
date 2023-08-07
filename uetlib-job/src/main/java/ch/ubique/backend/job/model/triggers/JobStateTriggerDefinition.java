package ch.ubique.backend.job.model.triggers;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRunState;

import jakarta.validation.constraints.NotNull;

public record JobStateTriggerDefinition(
        @NotNull Class<? extends Job> jobClass, @NotNull JobRunState state)
        implements TriggerDefinition {

    @Override
    @NotNull
    public TriggerType type() {
        return TriggerType.JOB_RUN_STATE;
    }
}
