package ch.ubique.uetlib.job.service.triggers;

import ch.ubique.uetlib.job.Job;
import ch.ubique.uetlib.job.data.service.JobDataService;
import ch.ubique.uetlib.job.model.JobClassAndId;
import ch.ubique.uetlib.job.model.JobRunArguments;
import ch.ubique.uetlib.job.model.triggers.*;
import ch.ubique.uetlib.job.service.JobService;
import ch.ubique.uetlib.job.service.executor.JobExecutorService;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

@Service
@Profile("!job-run")
public class TriggerCoordinatorService {

    private static final Logger logger = LoggerFactory.getLogger(TriggerCoordinatorService.class);

    private final boolean allTriggersDisabled;
    private final JobDataService jobDataService;

    private final JobService jobService;
    private final JobExecutorService jobExecutorService;

    private final Map<TriggerType, TriggerService<?>> triggerServices;

    public TriggerCoordinatorService(
            final JobDataService jobDataService,
            final JobService jobService,
            final JobExecutorService jobExecutorService,
            final Collection<TriggerService<?>> triggerServices,
            final Environment environment) {
        this.jobDataService = jobDataService;
        this.jobService = jobService;
        this.jobExecutorService = jobExecutorService;
        this.triggerServices =
                triggerServices.stream()
                        .collect(
                                Collectors.toMap(TriggerService::triggerType, Function.identity()));
        this.allTriggersDisabled = environment.acceptsProfiles(Profiles.of("job-disable-triggers"));
    }

    @EventListener(ApplicationReadyEvent.class)
    private void onStartup() {
        logger.info("Evaluating triggers of all jobs");
        final var existingDbTriggers = jobDataService.getAllDbTriggersByJobId();
        for (final var jobClass : jobService.getAllJobClasses()) {
            final var instance = jobService.instantiateJob(jobClass);
            final var triggers = getTriggers(instance, existingDbTriggers);
            final var classAndId = jobService.jobClassAndIdOrThrow(jobClass);
            for (final var trigger : triggers) {
                onStartup(trigger, classAndId, jobClass);
            }
        }
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
        getTriggerService(trigger.definition())
                .addTrigger(
                        trigger.id(),
                        trigger.definition(),
                        new JobClassAndId(jobId, jobClass.getCanonicalName()),
                        runnable(jobClass));
        logger.info("Added trigger {}", trigger);
    }

    public void removeTrigger(final Trigger trigger) {
        getTriggerService(trigger.definition()).removeTrigger(trigger.id());
        logger.info("Removed trigger {}", trigger);
    }

    /**
     * This method computes the effective job triggers and works as following:
     * <li>Query triggers in database
     * <li>If job has fixed triggers -> make sure they agree with database (override if not)
     * <li>If job has no fixed triggers and no database triggers -> create database entries for
     *     initial trigger
     * <li>return (possibly just created / overwritten) triggers from database
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
            if (dbTriggerDefinitions.equals(new HashSet<>(job.triggersConfig().triggers()))) {
                return dbTriggers;
            }
            // Fixed do not match db triggers -> Replace db triggers
            jobDataService.deleteTriggerForJob(jobClassAndId.id());
            return jobDataService.insertTriggers(
                    jobClassAndId.id(), job.triggersConfig().triggers());
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
            logger.info("Submitting job {}", jobClass);
            jobExecutorService.submit(jobClass, jobRunArguments);
        };
    }
}
