package ch.ubique.backend.job.service.triggers;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.JobRunStateTransition;
import ch.ubique.backend.job.model.triggers.JobRunStateTrigger;
import ch.ubique.backend.job.service.JobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Profile("!job-run")
public class JobRunStateTriggerService implements TriggerService<JobRunStateTrigger> {

    private static final Logger logger = LoggerFactory.getLogger(JobRunStateTriggerService.class);

    private final Map<Integer, JobRunStateTrigger> registeredTriggers;
    private final Map<Integer, Consumer<JobRunArguments>> triggerCallbacks;
    private final JobDataService jobDataService;
    private final JobService jobService;
    private Collection<Integer> lastCheckedJobRunStateIds = new HashSet<>();
    private Instant lastCheck;

    public JobRunStateTriggerService(
            final JobDataService jobDataService, final JobService jobService) {
        this.jobDataService = jobDataService;
        this.jobService = jobService;
        registeredTriggers = new ConcurrentHashMap<>();
        triggerCallbacks = new ConcurrentHashMap<>();
        lastCheck = Instant.now();
    }

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
    public void checkJobRuns() throws ClassNotFoundException {
        final var aBitBeforePreviousCheck = lastCheck.minus(Duration.ofMinutes(1));
        lastCheck = Instant.now();

        final var newStateTransitionsSinceABitBeforePreviousCheck =
                jobDataService.findJobRunStateTransitionsSince(aBitBeforePreviousCheck);
        final var newStateTransitionsSinceLastCheck =
                newStateTransitionsSinceABitBeforePreviousCheck.stream()
                        .filter(
                                jobRunStateTransition ->
                                        !lastCheckedJobRunStateIds.contains(
                                                jobRunStateTransition.id()))
                        .toList();

        // submit all state changes order by transitionedAt ascending (as returned by the data
        // service)
        for (final var newStateTransitions : newStateTransitionsSinceLastCheck) {
            onStateChanged(newStateTransitions.jobRun(), newStateTransitions.state());
        }

        lastCheckedJobRunStateIds =
                newStateTransitionsSinceLastCheck.stream()
                        .map(JobRunStateTransition::id)
                        .collect(Collectors.toUnmodifiableSet());
    }

    private void onStateChanged(final JobRun jobRun, final JobRunState newState)
            throws ClassNotFoundException {
        final var jobClass = jobService.getJobClass(jobRun.jobId());
        logger.debug("{} changed to state {}.", jobRun, newState);

        registeredTriggers.entrySet().stream()
                .filter(
                        entry -> {
                            final var trigger = entry.getValue();
                            return trigger.state() == newState
                                    && trigger.jobClass().equals(jobClass);
                        })
                .forEach(
                        entry -> {
                            final var consumer = triggerCallbacks.get(entry.getKey());
                            logger.debug("Calling callback for {}.", entry.getValue());
                            consumer.accept(new JobRunArguments());
                        });
    }

    @Override
    public void addTrigger(
            final int triggerId,
            final JobRunStateTrigger triggerDefinition,
            final Consumer<JobRunArguments> consumer) {
        registeredTriggers.put(triggerId, triggerDefinition);
        triggerCallbacks.put(triggerId, consumer);
    }

    @Override
    public void removeTrigger(final int triggerId) {
        registeredTriggers.remove(triggerId);
        triggerCallbacks.remove(triggerId);
    }
}
