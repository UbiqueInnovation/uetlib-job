package ch.ubique.backend.job.service.triggers;

import ch.ubique.backend.job.model.triggers.CronTrigger;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@Profile("!job-run")
public class CronTriggerService implements TriggerService<CronTrigger> {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    @NotNull private final ZoneId defaultZoneId;

    private final TaskScheduler taskScheduler;

    private final Map<Integer, ScheduledFuture<?>> scheduledCronJobs;

    public CronTriggerService(
            @Value("${ch.ubique.backend.job.cron.timezone:UTC}") @NotNull
                    final String defaultZoneId,
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
                                cronDefinition.cronExpression(),
                                Objects.requireNonNullElse(
                                        cronDefinition.timeZone(), defaultZoneId)));
        scheduledCronJobs.put(triggerId, scheduledFuture);
        logger.info("Scheduled cron trigger {} with definition {}", triggerId, cronDefinition);
    }

    public ZoneId getDefaultZoneId() {
        return defaultZoneId;
    }
}
