package ch.ubique.backend.job.service.triggers;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunState;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Profile("!job-run")
public class JobRunStateTriggerService implements TriggerService<JobRunStateTrigger> {

    private static final Logger logger = LoggerFactory.getLogger(JobRunStateTriggerService.class);

    private final Map<Integer, JobRunStateTrigger> registeredTriggers;
    private final Map<Integer, Consumer<JobRunArguments>> triggerCallbacks;
    private final JobDataService jobDataService;
    private final JobService jobService;
    private Collection<JobRun> activeJobRuns;
    private Instant lastCheck;

    public JobRunStateTriggerService(
            final JobDataService jobDataService, final JobService jobService) {
        this.jobDataService = jobDataService;
        this.jobService = jobService;
        registeredTriggers = new ConcurrentHashMap<>();
        triggerCallbacks = new ConcurrentHashMap<>();
        activeJobRuns = jobDataService.findAllCurrentRunnerJobRunsWithoutFinishedTimestamp();
        lastCheck = Instant.now();
    }

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
    public void checkJobRuns() throws ClassNotFoundException {
        final var aBitBeforePreviousCheck = lastCheck.minus(Duration.ofMinutes(1));
        lastCheck = Instant.now();

        // Check if the currently active jobs differ to before
        final var currentlyActive =
                jobDataService.findAllCurrentRunnerJobRunsWithoutFinishedTimestamp();
        final var previouslyActiveById =
                activeJobRuns.stream().collect(Collectors.toMap(JobRun::id, Function.identity()));
        for (final var jobRun : currentlyActive) {
            if (!previouslyActiveById.containsKey(jobRun.id())) { // Job Run is new
                onStateChanged(jobRun, jobRun.state());
            } else {
                final var previousState = previouslyActiveById.get(jobRun.id()).state();
                if (previousState != jobRun.state()) {
                    onStateChanged(jobRun, jobRun.state());
                }
            }
        }

        // Check if any jobs finished recently
        final var recentlyFinishedJobRuns =
                jobDataService.findJobRunsFinishedAfter(aBitBeforePreviousCheck);

        final var activeJobRunsById =
                activeJobRuns.stream().collect(Collectors.toMap(JobRun::id, Function.identity()));
        // check for state change on all recentlyFinishedJobRuns that were not active during the
        // last check
        for (final var recentlyFinishedJobRun : recentlyFinishedJobRuns) {
            if (!activeJobRunsById.containsKey(recentlyFinishedJobRun.id())) {
                final var currentState = recentlyFinishedJobRun.state();
                onStateChanged(recentlyFinishedJobRun, currentState);
            }
        }

        final var recentlyFinishedJobRunsById =
                recentlyFinishedJobRuns.stream()
                        .collect(Collectors.toMap(JobRun::id, Function.identity()));
        // check for state change on all activeJobRuns that recently finished
        for (final var activeJobRun : activeJobRuns) {
            if (recentlyFinishedJobRunsById.containsKey(activeJobRun.id())) {
                final var currentState = recentlyFinishedJobRunsById.get(activeJobRun.id()).state();
                onStateChanged(activeJobRun, currentState);
            }
        }

        this.activeJobRuns = currentlyActive;
    }

    private void onStateChanged(final JobRun jobRun, final JobRunState newState)
            throws ClassNotFoundException {
        final var jobClass = jobService.getJobClass(jobRun.jobId());
        logger.info("{} changed to state {}.", jobRun, newState);

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
                            logger.info("Calling callback for {}.", entry.getValue());
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
