package ch.ubique.backend.job.triggers;

import ch.ubique.backend.core.validator.ValidCron;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;

import java.util.TimeZone;

public record CronTrigger(@NotBlank @ValidCron String cronExpression, @Nullable TimeZone timeZone)
        implements Trigger {

    public CronTrigger(@NotBlank @ValidCron final String cronExpression) {
        this(cronExpression, null);
    }

    @Override
    @NotBlank
    public String type() {
        return "cron";
    }
}
