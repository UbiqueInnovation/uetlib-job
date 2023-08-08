package ch.ubique.backend.job.service;

import static java.util.Optional.ofNullable;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.triggers.CronTriggerDefinition;
import ch.ubique.backend.job.model.triggers.StartupTriggerDefinition;
import ch.ubique.backend.job.model.triggers.Trigger;

import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

@Service
public class TriggerService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobDataService jobDataService;
    private final ZoneId defaultZoneId;
    private final JobExecutorService jobExecutorService;

    private final JobService jobService;
    private final TaskScheduler taskScheduler;

    private final Map<Integer, ScheduledFuture<?>> scheduledCronJobs;

    // TODO Create sub-service for each trigger type
    public TriggerService(
            final JobDataService jobDataService,
            @Value("${ch.ubique.backend.job.cron.timezone:UTC}") final String defaultZoneId,
            final JobExecutorService jobExecutorService,
            final JobService jobService,
            final TaskScheduler taskScheduler)
            throws ClassNotFoundException {
        this.jobDataService = jobDataService;
        this.jobService = jobService;
        this.defaultZoneId = ZoneId.of(defaultZoneId);
        this.jobExecutorService = jobExecutorService;
        this.taskScheduler = taskScheduler;

        onStartup();
        scheduledCronJobs = new ConcurrentHashMap<>();
    }

    private void onStartup() throws ClassNotFoundException {
        logger.info("Evaluating triggers of all jobs");
        for (final var job : jobDataService.findJobs()) {
            final var jobClass = jobService.getJobClass(job.canonicalClassName());
            final var instance = jobService.instantiateJob(jobClass);
            final var triggers = getTriggers(instance);
            for (final var trigger : triggers) {
                onStartup(jobClass, trigger);
            }
        }
    }

    private void onStartup(final Class<? extends Job> jobClass, final Trigger trigger) {
        final var triggerDefinition = trigger.definition();
        if (triggerDefinition instanceof StartupTriggerDefinition) {
            jobExecutorService.execute(jobClass);
        } else if (triggerDefinition instanceof final CronTriggerDefinition cronDefinition) {
            scheduleCronTrigger(jobClass, trigger.id(), cronDefinition);
        } else {
            throw new NotImplementedException(trigger.getClass() + " not supported yet.");
        }
    }

    private void scheduleCronTrigger(
            final Class<? extends Job> jobClass,
            final int triggerId,
            final CronTriggerDefinition cronDefinition) {
        final var scheduledFuture =
                taskScheduler.schedule(
                        () -> jobExecutorService.execute(jobClass),
                        new org.springframework.scheduling.support.CronTrigger(
                                cronDefinition.cronExpression(),
                                ofNullable(cronDefinition.timeZone()).orElse(defaultZoneId)));
        scheduledCronJobs.put(triggerId, scheduledFuture);
        logger.info("Scheduled cron task {}", cronDefinition);
    }

    private void addTrigger(final Class<? extends Job> jobClass, final Trigger trigger) {
        final var triggerDefinition = trigger.definition();
        if (triggerDefinition instanceof StartupTriggerDefinition) {
            throw new UnsupportedOperationException(
                    "Can not add " + StartupTriggerDefinition.class + " after startup.");
        }
        if (triggerDefinition instanceof final CronTriggerDefinition cronDefinition) {
            scheduleCronTrigger(jobClass, trigger.id(), cronDefinition);
        } else {
            throw new NotImplementedException(trigger.getClass() + " not supported yet.");
        }
        logger.info("Added trigger {}", trigger);
    }

    public void removeTrigger(final Trigger trigger) {
        if (scheduledCronJobs.containsKey(trigger.id())) {
            scheduledCronJobs.get(trigger.id()).cancel(false);
        }
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
    public Collection<Trigger> getTriggers(final Job job) {
        final var jobId = jobService.getId(job);
        final var dbTriggers = jobDataService.getTriggers(jobId);
        final var dbTriggerDefinitions =
                dbTriggers.stream().map(Trigger::definition).collect(Collectors.toSet());

        if (job.triggersConfig().isFixed()) {
            if (dbTriggerDefinitions.equals(new HashSet<>(job.triggersConfig().triggers()))) {
                return dbTriggers;
            } else {
                // Fixed do not match db triggers -> Replace db triggers
                jobDataService.deleteTriggerForJob(jobId);
                return jobDataService.insertTriggers(jobId, job.triggersConfig().triggers());
            }
        }

        // There are triggers present in database -> return them
        if (!dbTriggers.isEmpty()) {
            return dbTriggers;
        }

        // Insert initial triggers into to database
        return jobDataService.insertTriggers(jobId, job.triggersConfig().triggers());
    }

    public void updateTrigger(final Class<? extends Job> jobClass, final Trigger trigger) {
        removeTrigger(trigger);
        addTrigger(jobClass, trigger);
        jobDataService.saveTrigger(trigger);
    }

    public ZoneId getDefaultZoneId() {
        return defaultZoneId;
    }
}
