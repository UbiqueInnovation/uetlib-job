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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class TriggerService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobDataService jobDataService;
    private final ZoneId defaultZoneId;
    private final JobExecutorService jobExecutorService;

    private final JobService jobService;
    private final TaskScheduler taskScheduler;

    private final Map<Integer, ScheduledFuture<?>> scheduledCronJobs;

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

        addTriggers();
        scheduledCronJobs = new ConcurrentHashMap<>();
    }

    public void addTriggers() throws ClassNotFoundException {
        logger.info("Evaluating triggers of all jobs");
        for (final var job : jobDataService.findJobs()) {
            final var jobClass = jobService.getJobClass(job.canonicalClassName());
            final var instance = jobService.instantiateJob(jobClass);
            final var triggers = getTriggers(instance);
            for (final var trigger : triggers) {
                addTrigger(jobClass, trigger);
            }
        }
    }

    public void addTrigger(final Class<? extends Job> jobClass, final Trigger trigger) {
        final var triggerDefinition = trigger.definition();
        if (triggerDefinition instanceof StartupTriggerDefinition) {
            jobExecutorService.execute(jobClass);
        } else if (triggerDefinition instanceof final CronTriggerDefinition cronDefinition) {
            final var scheduledFuture =
                    taskScheduler.schedule(
                            () -> jobExecutorService.execute(jobClass),
                            new org.springframework.scheduling.support.CronTrigger(
                                    cronDefinition.cronExpression(),
                                    ofNullable(cronDefinition.timeZone()).orElse(defaultZoneId)));
            scheduledCronJobs.put(trigger.id(), scheduledFuture);
        } else {
            throw new NotImplementedException(trigger.getClass() + " not supported yet.");
        }
    }

    public void removeTrigger(final Trigger trigger) {
        if (scheduledCronJobs.containsKey(trigger.id())) {
            scheduledCronJobs.get(trigger.id()).cancel(false);
        }
    }

    /**
     * This method computes the effective job triggers and works as following:
     * <li>If the job has fixed triggers -> return them
     * <li>Else-If there is at least one trigger in the database -> return triggers from database
     * <li>Else: return initial (potentially empty) list of triggers
     */
    public Collection<Trigger> getTriggers(final Job job) {
        if (job.triggersConfig().isFixed()) {
            return job.triggersConfig().triggers();
        }

        final var dbTriggers = jobDataService.getTriggers(jobService.getId(job));

        if (!dbTriggers.isEmpty()) {
            return dbTriggers;
        }

        // Return initial triggers
        return job.triggersConfig().triggers();
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
