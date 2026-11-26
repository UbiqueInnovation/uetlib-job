package ch.uetlib.job.service.triggers;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.triggers.FixedRateTrigger;
import ch.uetlib.job.model.triggers.TriggerType;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Service
@Profile("!job-run")
public class FixedRateTriggerService implements TriggerService<FixedRateTrigger> {

    private static final Logger logger = LoggerFactory.getLogger(FixedRateTriggerService.class);

    private final TaskScheduler taskScheduler;
    private final JobDataService jobDataService;

    private final Map<Integer, Registration> registrations = new HashMap<>();
    private final Map<Integer, ScheduledFuture<?>> scheduledJobs = new HashMap<>();
    private boolean primary;

    public FixedRateTriggerService(
            final TaskScheduler taskScheduler, final JobDataService jobDataService) {
        this.taskScheduler = taskScheduler;
        this.jobDataService = jobDataService;
    }

    @Override
    public synchronized void addTrigger(
            final int triggerId,
            final FixedRateTrigger triggerDefinition,
            final JobClassAndId job,
            final Consumer<JobRunArguments> consumer) {
        removeTrigger(triggerId);
        final var earliestStart =
                Instant.now()
                        .plus(
                                Objects.requireNonNullElse(
                                        triggerDefinition.initialDelay(), Duration.ZERO));
        final Runnable fire =
                () ->
                        consumer.accept(
                                new JobRunArguments(
                                        false, TriggerType.FIXED_RATE, null, null, null));
        final var registration = new Registration(triggerDefinition, job, fire, earliestStart);
        registrations.put(triggerId, registration);
        if (primary) {
            schedule(triggerId, registration);
        }
    }

    @Override
    public synchronized void removeTrigger(final int triggerId) {
        registrations.remove(triggerId);
        cancel(triggerId);
    }

    @Override
    public synchronized void onPrimaryLease(final boolean holdsLease) {
        primary = holdsLease;
        // every 30s only, and no DB lookup, so no short-circuiting required
        if (holdsLease) {
            registrations.forEach((triggerId, registration) -> {
                if (!scheduledJobs.containsKey(triggerId)) {
                    schedule(triggerId, registration);
                }
            });
        } else {
            // Copy to prevent concurrent modification
            List.copyOf(scheduledJobs.keySet()).forEach(this::cancel);
        }
    }

    synchronized boolean isScheduled(final int triggerId) {
        return scheduledJobs.containsKey(triggerId);
    }

    static Instant firstFiring(
            final Instant now,
            final Instant earliestJobTriggerStart,
            final @Nullable Instant lastQueuedAt,
            final Duration period) {
        final var phase = lastQueuedAt == null ? now : lastQueuedAt.plus(period);
        return Stream.of(now, earliestJobTriggerStart, phase).max(Instant::compareTo).orElseThrow();
    }

    private void schedule(final int triggerId, final Registration registration) {
        final var period = registration.definition().period();
        final Instant lastQueuedAt;
        try {
            lastQueuedAt =
                    jobDataService
                            .findLastQueuedAt(registration.job().id(), TriggerType.FIXED_RATE)
                            .orElse(null);
        } catch (final RuntimeException e) {
            logger.warn(
                    "Could not look up when {} was last queued by a fixed-rate trigger, {}.",
                    registration.job(),
                    triggerId,
                    e);
            return;
        }
        final var start =
                firstFiring(
                        Instant.now(),
                        registration.earliestJobTriggerStart(),
                        lastQueuedAt,
                        period);
        scheduledJobs.put(
                triggerId, taskScheduler.scheduleAtFixedRate(registration.exec(), start, period));
        logger.info(
                "Scheduled fixed-rate trigger {} of {} every {} from {} (last fixed-rate queueing:"
                        + " {}).",
                triggerId,
                registration.job(),
                period,
                start,
                lastQueuedAt);
    }

    private void cancel(final int triggerId) {
        final var future = scheduledJobs.remove(triggerId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private record Registration(
            FixedRateTrigger definition,
            JobClassAndId job,
            Runnable exec,
            Instant earliestJobTriggerStart) {}
}
