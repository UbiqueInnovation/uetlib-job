package ch.ubique.backend.job.service.triggers;

import ch.ubique.backend.job.model.triggers.FixedRateTrigger;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@Profile("!job-run")
public class FixedRateTriggerService implements TriggerService<FixedRateTrigger> {

    private final TaskScheduler taskScheduler;
    private final Map<Integer, ScheduledFuture<?>> scheduledJobs;

    public FixedRateTriggerService(final TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
        this.scheduledJobs = new ConcurrentHashMap<>();
    }

    @Override
    public void onStartup(
            final int triggerId,
            final FixedRateTrigger triggerDefinition,
            final Runnable runnable) {
        scheduledJobs.put(
                triggerId, taskScheduler.scheduleAtFixedRate(runnable, triggerDefinition.period()));
    }

    @Override
    public void addTrigger(
            final int triggerId,
            final FixedRateTrigger triggerDefinition,
            final Runnable runnable) {
        scheduledJobs.put(
                triggerId, taskScheduler.scheduleAtFixedRate(runnable, triggerDefinition.period()));
    }

    @Override
    public void removeTrigger(final int triggerId) {
        if (scheduledJobs.containsKey(triggerId)) {
            scheduledJobs.remove(triggerId).cancel(false);
        }
    }
}
