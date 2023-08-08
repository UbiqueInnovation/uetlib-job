package ch.ubique.backend.job.service.triggers;

import static java.util.Optional.ofNullable;

import ch.ubique.backend.job.model.triggers.CronTrigger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

@Service
public class CronTriggerService implements TriggerService<CronTrigger> {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ZoneId defaultZoneId;

    private final TaskScheduler taskScheduler;

    private final Map<Integer, ScheduledFuture<?>> scheduledCronJobs;

    public CronTriggerService(
            @Value("${ch.ubique.backend.job.cron.timezone:UTC}") final String defaultZoneId,
            final TaskScheduler taskScheduler) {
        this.defaultZoneId = ZoneId.of(defaultZoneId);
        this.taskScheduler = taskScheduler;
        this.scheduledCronJobs = new ConcurrentHashMap<>();
    }

    @Override
    public void onStartup(
            final int triggerId, final CronTrigger triggerDefinition, final Runnable runnable) {
        scheduleCronTrigger(triggerId, triggerDefinition, runnable);
    }

    @Override
    public void addTrigger(
            final int triggerId, final CronTrigger triggerDefinition, final Runnable runnable) {
        scheduleCronTrigger(triggerId, triggerDefinition, runnable);
    }

    @Override
    public void removeTrigger(final int triggerId) {
        if (scheduledCronJobs.containsKey(triggerId)) {
            scheduledCronJobs.get(triggerId).cancel(false);
            scheduledCronJobs.remove(triggerId);
        }
    }

    private void scheduleCronTrigger(
            final int triggerId, final CronTrigger cronDefinition, final Runnable runnable) {
        final var scheduledFuture =
                taskScheduler.schedule(
                        runnable,
                        new org.springframework.scheduling.support.CronTrigger(
                                validateCronExpression(cronDefinition),
                                ofNullable(cronDefinition.timeZone()).orElse(defaultZoneId)));
        scheduledCronJobs.put(triggerId, scheduledFuture);
        logger.info("Scheduled cron trigger {} with definition {}", triggerId, cronDefinition);
    }

    private static String validateCronExpression(final CronTrigger cronDefinition) {
        final var expression = cronDefinition.cronExpression();
        if (CronExpression.isValidExpression(expression)) {
            return expression;
        }

        // Strip additional tokes from the end
        final var limitToSixTokens =
                Arrays.stream(StringUtils.tokenizeToStringArray(expression, " "))
                        .limit(6)
                        .collect(Collectors.joining(" "));
        if (CronExpression.isValidExpression(limitToSixTokens)) {
            return limitToSixTokens;
        }

        throw new IllegalArgumentException(
                "The specified cron expression is invalid: " + expression);
    }

    public ZoneId getDefaultZoneId() {
        return defaultZoneId;
    }
}
