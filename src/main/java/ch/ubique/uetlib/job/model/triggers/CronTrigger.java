package ch.ubique.uetlib.job.model.triggers;

import ch.ubique.uetlib.spring.boot.utils.CronValidationUtils;
import ch.ubique.uetlib.spring.boot.validator.ValidCron;

import com.fasterxml.jackson.annotation.JsonCreator;

import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;

import java.time.ZoneId;

public record CronTrigger(@NotBlank @ValidCron String cronExpression, @Nullable ZoneId timeZone)
        implements TriggerDefinition {

    @JsonCreator
    public CronTrigger(
            @NotBlank @ValidCron final String cronExpression, @Nullable final ZoneId timeZone) {
        this.cronExpression = CronValidationUtils.tryToGetValidCronExpression(cronExpression);
        this.timeZone = timeZone;
    }

    public CronTrigger(@NotBlank @ValidCron final String cronExpression) {
        this(cronExpression, null);
    }
}
