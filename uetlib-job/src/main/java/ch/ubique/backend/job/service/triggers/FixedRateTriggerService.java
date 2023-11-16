package ch.ubique.backend.job.service.triggers;

import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.triggers.FixedRateTrigger;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

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
    public void addTrigger(
            final int triggerId,
            final FixedRateTrigger triggerDefinition,
            final Consumer<JobRunArguments> consumer) {
        final var now = Instant.now();
        final var startTime =
                Optional.ofNullable(triggerDefinition.initialDelay()).map(now::plus).orElse(now);
        final var period = triggerDefinition.period();
        final Runnable runnable = () -> consumer.accept(new JobRunArguments());
        scheduledJobs.put(
                triggerId, taskScheduler.scheduleAtFixedRate(runnable, startTime, period));
    }

    @Override
    public void removeTrigger(final int triggerId) {
        if (scheduledJobs.containsKey(triggerId)) {
            scheduledJobs.remove(triggerId).cancel(false);
        }
    }
}
