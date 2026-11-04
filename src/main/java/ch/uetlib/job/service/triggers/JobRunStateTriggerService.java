package ch.uetlib.job.service.triggers;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.JobRunStateTransition;
import ch.uetlib.job.model.triggers.JobRunStateTrigger;
import ch.uetlib.job.model.triggers.TriggerType;
import ch.uetlib.job.service.JobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
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

    @SuppressWarnings("java:S1450")
    private Collection<Integer> lastCheckedJobRunStateIds = Set.of();

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
    public void checkJobRuns() {
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

    private void onStateChanged(final JobRun jobRun, final JobRunState newState) {
        final var jobClass = jobService.findJobByIdOrThrow(jobRun.jobId());
        logger.debug("{} changed to state {}.", jobRun, newState);

        registeredTriggers.entrySet().stream()
                .filter(
                        entry -> {
                            final var trigger = entry.getValue();
                            return trigger.state() == newState
                                    && trigger.jobClass()
                                            .getCanonicalName()
                                            .equals(jobClass.canonicalClassName());
                        })
                .forEach(
                        entry -> {
                            final var consumer = triggerCallbacks.get(entry.getKey());
                            if (consumer != null) {
                                logger.debug("Calling callback for {}.", entry.getValue());
                                consumer.accept(
                                        new JobRunArguments(
                                                false,
                                                TriggerType.JOB_RUN_STATE,
                                                null,
                                                null,
                                                null));
                            }
                        });
    }

    @Override
    public void addTrigger(
            final int triggerId,
            final JobRunStateTrigger triggerDefinition,
            final JobClassAndId job,
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
