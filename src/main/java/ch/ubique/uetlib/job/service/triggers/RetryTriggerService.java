package ch.ubique.uetlib.job.service.triggers;

import static ch.ubique.uetlib.job.model.JobRunState.PROBLEM_STATES;

import static org.apache.commons.lang3.compare.ComparableUtils.is;

import static java.util.Objects.requireNonNullElse;

import ch.ubique.uetlib.job.data.service.JobDataService;
import ch.ubique.uetlib.job.model.JobClassAndId;
import ch.ubique.uetlib.job.model.JobRun;
import ch.ubique.uetlib.job.model.JobRunArguments;
import ch.ubique.uetlib.job.model.RetryTriggerContext;
import ch.ubique.uetlib.job.model.triggers.RetryTrigger;
import ch.ubique.uetlib.job.model.triggers.TriggerType;

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

@Service
@Profile("!job-run")
public class RetryTriggerService implements TriggerService<RetryTrigger> {

    private static final Logger logger = LoggerFactory.getLogger(RetryTriggerService.class);

    private final Map<Integer, JobClassAndId> jobsByTriggerId;
    private final Map<Integer, RetryTrigger> registeredTriggers;
    private final Map<Integer, Consumer<JobRunArguments>> triggerCallbacks;
    private final JobDataService jobDataService;

    public RetryTriggerService(final JobDataService jobDataService) {
        this.jobDataService = jobDataService;
        this.registeredTriggers = new ConcurrentHashMap<>();
        this.triggerCallbacks = new ConcurrentHashMap<>();
        this.jobsByTriggerId = new ConcurrentHashMap<>();
    }

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
    public void checkJobRuns() {
        for (final var jobRun : jobDataService.findLatestRunsForEachJobIfInState(PROBLEM_STATES)) {
            logger.debug("Evaluating if {} needs to be retried.", jobRun);
            for (final var triggerId : retryTriggerIdsOfJob(jobRun.jobId())) {
                evaluateTrigger(jobRun, triggerId);
            }
        }
    }

    private Collection<Integer> retryTriggerIdsOfJob(final int jobId) {
        return registeredTriggers.keySet().stream()
                .filter(
                        triggerId -> {
                            final var job = jobsByTriggerId.get(triggerId);
                            return job != null && job.id() == jobId;
                        })
                .toList();
    }

    private void evaluateTrigger(final JobRun jobRun, final Integer triggerId) {
        final var trigger = registeredTriggers.get(triggerId);
        if (trigger == null) {
            return;
        }
        logger.debug("Evaluating {} for {}.", trigger, jobRun);

        if (!is(age(jobRun)).greaterThan(trigger.constantBackoff())) {
            logger.debug("Backoff for {} is not yet over.", jobRun);
            return;
        }

        final var retryContext = getOrCreateRetryTriggerContext(jobRun, trigger);
        if (!retryContext.canBeRetried()) {
            logger.debug("Max retries for {} reached.", jobRun);
            return;
        }

        logger.info("Requesting retry for {}.", jobRun);
        final var callback = triggerCallbacks.get(triggerId);
        if (callback != null) {
            callback.accept(
                    new JobRunArguments(
                            false, TriggerType.RETRY, retryContext.increment(), null, null));
        }
    }

    private RetryTriggerContext getOrCreateRetryTriggerContext(
            final JobRun jobRun, final RetryTrigger trigger) {
        final var args = jobRun.jobRunArguments();
        if (args != null && args.triggerContext() instanceof final RetryTriggerContext context) {
            return context;
        }
        return new RetryTriggerContext(0, trigger.maxRetries(), jobRun.id());
    }

    private Duration age(final JobRun jobRun) {
        final var jobRunEnd = requireNonNullElse(jobRun.finishedAt(), jobRun.startedAt());
        return Duration.between(jobRunEnd, Instant.now());
    }

    @Override
    public void addTrigger(
            final int triggerId,
            final RetryTrigger triggerDefinition,
            final JobClassAndId job,
            final Consumer<JobRunArguments> consumer) {
        jobsByTriggerId.put(triggerId, job);
        registeredTriggers.put(triggerId, triggerDefinition);
        triggerCallbacks.put(triggerId, consumer);
    }

    @Override
    public void removeTrigger(final int triggerId) {
        registeredTriggers.remove(triggerId);
        triggerCallbacks.remove(triggerId);
        jobsByTriggerId.remove(triggerId);
    }
}
