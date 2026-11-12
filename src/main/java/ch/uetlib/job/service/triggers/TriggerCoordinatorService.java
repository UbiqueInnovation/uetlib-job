package ch.uetlib.job.service.triggers;

import ch.uetlib.job.Job;
import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.triggers.*;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.instance.JobInstanceIdentity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Profile("!job-run & " + TriggerCoordinatorService.PRIMARY_TRIGGERS_PROFILE)
public class TriggerCoordinatorService {

    private static final Logger logger = LoggerFactory.getLogger(TriggerCoordinatorService.class);

    /** Instances evaluating triggers; only the primary lease holder queues runs from them. */
    public static final String PRIMARY_TRIGGERS_PROFILE = "job-primary-triggers";

    private final boolean allTriggersDisabled;
    private final JobDataService jobDataService;

    private final JobService jobService;
    private final JobInstanceIdentity instanceIdentity;

    private final Map<TriggerType, TriggerService<?>> triggerServices;

    private final AtomicInteger registeredTriggers = new AtomicInteger();
    private volatile boolean started;

    public TriggerCoordinatorService(
            final JobDataService jobDataService,
            final JobService jobService,
            final JobInstanceIdentity instanceIdentity,
            final Collection<TriggerService<?>> triggerServices,
            final Environment environment) {
        this.jobDataService = jobDataService;
        this.jobService = jobService;
        this.instanceIdentity = instanceIdentity;
        this.triggerServices =
                triggerServices.stream()
                        .collect(
                                Collectors.toMap(TriggerService::triggerType, Function.identity()));
        this.allTriggersDisabled =
                environment.acceptsProfiles(Profiles.of("job-disable-triggers"))
                        || !environment.acceptsProfiles(Profiles.of(PRIMARY_TRIGGERS_PROFILE));
    }

    @EventListener(ApplicationReadyEvent.class)
    private void onStartup() {
        if (allTriggersDisabled) {
            logger.info("Not registering live triggers on this instance.");
            return;
        }
        logger.info("Evaluating triggers of all jobs");
        final var existingDbTriggers = jobDataService.getAllDbTriggersByJobId();
        for (final var jobClass : jobService.getAllJobClasses()) {
            final var instance = jobService.instantiateJob(jobClass);
            final var triggers = getTriggers(instance, existingDbTriggers);
            final var classAndId = jobService.jobClassAndIdOrThrow(jobClass);
            for (final var trigger : triggers) {
                onStartup(trigger, classAndId, jobClass);
                registeredTriggers.incrementAndGet();
            }
        }
        started = true;
        logger.info(
                "Registered {} live trigger(s). They enqueue runs only while this instance holds the"
                        + " primary lease.",
                registeredTriggers.get());
    }

    /** Whether this instance competes for the lease: it has the profile and triggers enabled. */
    public boolean isPrimaryCandidate() {
        return !allTriggersDisabled;
    }

    /** Whether the triggers are registered; only then may this instance take the lease. */
    public boolean isStarted() {
        return started;
    }

    /** How many triggers this instance actually registered - 0 means it fires nothing. */
    public int registeredTriggerCount() {
        return registeredTriggers.get();
    }

    protected void onStartup(
            final Trigger trigger, final JobClassAndId job, final Class<? extends Job> jobClass) {
        getTriggerService(trigger.definition())
                .onStartup(trigger.id(), trigger.definition(), job, runnable(jobClass));
    }

    public void createTrigger(
            final Class<? extends Job> jobClass, final TriggerDefinition triggerDefinition) {
        final var jobId = jobService.findJobIdOrThrow(jobClass);
        final var trigger = jobDataService.insertTrigger(jobId, triggerDefinition);
        addTrigger(jobId, jobClass, trigger);
    }

    public void deleteTrigger(final Trigger trigger) {
        removeTrigger(trigger);
        jobDataService.deleteTrigger(trigger);
        logger.info("Deleted trigger {}", trigger);
    }

    public void addTrigger(
            final int jobId, final Class<? extends Job> jobClass, final Trigger trigger) {
        if (allTriggersDisabled) {
            logger.debug("Not registering trigger {} live on this instance.", trigger);
            return;
        }
        getTriggerService(trigger.definition())
                .addTrigger(
                        trigger.id(),
                        trigger.definition(),
                        new JobClassAndId(jobId, jobClass.getCanonicalName()),
                        runnable(jobClass));
        logger.info("Added trigger {}", trigger);
    }

    public void removeTrigger(final Trigger trigger) {
        if (allTriggersDisabled) {
            logger.debug(
                    "No live trigger registration for {} to remove on this instance.", trigger);
            return;
        }
        getTriggerService(trigger.definition()).removeTrigger(trigger.id());
        logger.info("Removed trigger {}", trigger);
    }

    /**
     * This method computes the effective job triggers and works as following:
     * <li>Query triggers in database
     * <li>If job has fixed triggers -> reconcile with database (add/remove the difference) if not
     *     already in agreement
     * <li>If job has no fixed triggers and no database triggers -> create database entries for
     *     initial trigger
     * <li>return (possibly just reconciled / created) triggers from database
     */
    public Collection<Trigger> getTriggers(
            final Job job, final Map<Integer, List<Trigger>> existingDbTriggersByJob) {
        final var jobClassAndId = jobService.jobClassAndIdOrThrow(job.getClass());
        final var dbTriggers =
                Objects.<List<Trigger>>requireNonNullElseGet(
                        existingDbTriggersByJob.get(jobClassAndId.id()), List::of);
        final var dbTriggerDefinitions =
                dbTriggers.stream().map(Trigger::definition).collect(Collectors.toSet());

        if (job.triggersConfig().isFixed()) {
            final var targetDefinitions = new HashSet<>(job.triggersConfig().triggers());
            if (dbTriggerDefinitions.equals(targetDefinitions)) {
                return dbTriggers;
            }
            final var keptTriggers =
                    dbTriggers.stream()
                            .filter(trigger -> targetDefinitions.contains(trigger.definition()))
                            .toList();
            dbTriggers.stream()
                    .filter(trigger -> !targetDefinitions.contains(trigger.definition()))
                    .forEach(jobDataService::deleteTrigger);
            final var newDefinitions =
                    targetDefinitions.stream()
                            .filter(definition -> !dbTriggerDefinitions.contains(definition))
                            .toList();
            final var insertedTriggers =
                    newDefinitions.isEmpty()
                            ? List.<Trigger>of()
                            : jobDataService.insertTriggers(jobClassAndId.id(), newDefinitions);
            return Stream.concat(keptTriggers.stream(), insertedTriggers.stream()).toList();
        }

        // There are triggers present in the database -> return them
        if (!dbTriggers.isEmpty()) {
            return dbTriggers;
        }

        // Insert initial triggers into to database
        return jobDataService.insertTriggers(jobClassAndId.id(), job.triggersConfig().triggers());
    }

    public void updateTrigger(final Class<? extends Job> jobClass, final Trigger trigger) {
        removeTrigger(trigger);
        final var jobId = jobService.findJobIdOrThrow(jobClass);
        addTrigger(jobId, jobClass, trigger);
        jobDataService.saveTrigger(trigger);
    }

    @SuppressWarnings("unchecked")
    private <T extends TriggerDefinition> TriggerService<T> getTriggerService(
            final T triggerDefinition) {
        final var type = triggerDefinition.type();
        final var triggerService = (TriggerService<T>) triggerServices.get(type);
        return Objects.requireNonNull(
                triggerService,
                () -> MessageFormat.format("No {0} for {1} found.", TriggerService.class, type));
    }

    private Consumer<JobRunArguments> runnable(final Class<? extends Job> jobClass) {
        if (allTriggersDisabled) {
            return jobRunArguments ->
                    logger.info(
                            "Job {} will not be submitted because all triggers are disabled by profile.",
                            jobClass.getSimpleName());
        }
        return jobRunArguments -> {
            final var queued =
                    jobService.queueNewRunAsPrimary(
                            instanceIdentity.name(),
                            jobService.jobClassAndIdOrThrow(jobClass),
                            jobRunArguments);
            if (queued.isEmpty()) {
                logger.debug(
                        "Not queueing job {}: this instance does not hold the primary lease.",
                        jobClass);
                return;
            }
            logger.info("Queued job {}", jobClass);
        };
    }
}
