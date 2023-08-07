package ch.ubique.backend.job.model.triggers;

import ch.ubique.backend.core.validator.ValidCron;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZoneId;

public record CronTrigger(@NotBlank @ValidCron String cronExpression, @Nullable ZoneId timeZone)
        implements Trigger {

    public CronTrigger(@NotBlank @ValidCron final String cronExpression) {
        this(cronExpression, null);
    }

    @Override
    @NotNull
    public TriggerType type() {
        return TriggerType.CRON;
    }
}
