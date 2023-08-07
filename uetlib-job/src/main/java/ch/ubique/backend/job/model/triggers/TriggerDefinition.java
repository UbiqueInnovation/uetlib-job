package ch.ubique.backend.job.model.triggers;

import jakarta.validation.constraints.NotNull;

public sealed interface TriggerDefinition
        permits CronTriggerDefinition, JobStateTriggerDefinition, StartupTriggerDefinition {
    @NotNull
    TriggerType type();
}
