package ch.ubique.backend.job.model.triggers;

import ch.ubique.backend.core.validator.ValidCron;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;

import java.time.ZoneId;

public record CronTriggerDefinition(
        @NotBlank @ValidCron String cronExpression, @Nullable ZoneId timeZone)
        implements TriggerDefinition {

    public CronTriggerDefinition(@NotBlank @ValidCron final String cronExpression) {
        this(cronExpression, null);
    }
}
