package ch.ubique.backend.job.model.triggers;

import jakarta.validation.constraints.NotNull;

public sealed interface Trigger permits CronTrigger, JobStateTrigger, StartupTrigger {
    @NotNull
    TriggerType type();
}
