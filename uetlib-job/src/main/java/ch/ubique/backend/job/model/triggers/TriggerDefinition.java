package ch.ubique.backend.job.model.triggers;

import jakarta.validation.constraints.NotNull;

public sealed interface TriggerDefinition
        permits CronTrigger, FixedRateTrigger, JobStateTrigger, RunOnStartupTrigger {
    @NotNull
    default TriggerType type() {
        return TriggerType.getTypeForClass(getClass());
    }
}
