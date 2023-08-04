package ch.ubique.backend.job.triggers;

import jakarta.validation.constraints.NotBlank;

public sealed interface Trigger permits CronTrigger, JobStateTrigger, StartupTrigger {
    @NotBlank
    String type();
}
