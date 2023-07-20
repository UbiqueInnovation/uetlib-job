package ch.ubique.backend.job.scheduler;

import ch.ubique.backend.job.BaseJob;

import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.ZoneId;
import java.util.*;

@Service
public class SchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerService.class);
    private final Scheduler scheduler;

    private final ZoneId zoneId;

    public SchedulerService(
            final Scheduler scheduler,
            @Value("${ch.ubique.backend.job.cron.timezone:UTC}") final String zoneId) {
        this.scheduler = scheduler;
        this.zoneId = ZoneId.of(zoneId);
    }

    private static JobDetail createJobDetail(final JobKey key, final Job job) {
        return JobBuilder.newJob(job.getClass()).withIdentity(key).build();
    }

    public void scheduleJob(
            final String identifier, final BaseJob job, final String cronExpression) {
        try {
            logger.info("Scheduling {} with cron expression: {}", identifier, cronExpression);
            final var key = new JobKey(identifier);

            final var jobDetail = createJobDetail(key, job);

            final var trigger =
                    TriggerBuilder.newTrigger()
                            .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                            .withPriority(job.priority())
                            .build();

            scheduler.scheduleJob(jobDetail, trigger);

        } catch (final SchedulerException e) {
            logger.error("Could not schedule job for: {}", identifier, e);
        }
    }

    public void updateJob(final String identifier, final BaseJob job) {
        final var cronExpressionString = job.getCronExpression();
        try {
            logger.info(
                    "Updating scheduled job {} with cron expression: {}",
                    identifier,
                    cronExpressionString);
            final var key = new JobKey(identifier);
            final var jobDetail = getOrCreateJobDetail(key, job);

            removeScheduleJob(identifier);

            final var cronExpression = new CronExpression(cronExpressionString);
            cronExpression.setTimeZone(TimeZone.getTimeZone(zoneId));

            final var trigger =
                    TriggerBuilder.newTrigger()
                            .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                            .withPriority(job.priority())
                            .build();
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (final SchedulerException | ParseException e) {
            logger.error("Could not update job for: {}", identifier, e);
        }
    }

    public void removeScheduleJob(final String identifier) {
        try {
            logger.info("Removing scheduled job {}", identifier);
            final var key = new JobKey(identifier);

            final var triggers = scheduler.getTriggersOfJob(key);

            for (final var trigger : triggers) {
                scheduler.unscheduleJob(trigger.getKey());
            }
        } catch (final SchedulerException e) {
            logger.error("Could not remove job {}", identifier, e);
        }
    }

    public void runJobNowManual(
            final String identifier, final BaseJob job, final boolean forceRun) {
        final var manualIdentifier = identifier + "_MANUAL_" + UUID.randomUUID();
        try {
            logger.info(
                    "Manually scheduled job {} to run now. forceRun: {}",
                    manualIdentifier,
                    forceRun);
            final var key = new JobKey(manualIdentifier);
            final var jobDetail = createJobDetail(key, job);
            jobDetail.getJobDataMap().put("forceRun", forceRun);
            final var trigger =
                    TriggerBuilder.newTrigger().startNow().withPriority(job.priority()).build();
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (final SchedulerException e) {
            logger.error(
                    "Could not schedule job {} now. forceRun: {}", manualIdentifier, forceRun, e);
        }
    }

    private JobDetail getOrCreateJobDetail(final JobKey key, final Job job)
            throws SchedulerException {
        return Objects.requireNonNullElseGet(
                scheduler.getJobDetail(key), () -> createJobDetail(key, job));
    }

    public Collection<TriggerKey> getAllTriggerKeys() throws SchedulerException {
        return scheduler.getTriggerKeys(GroupMatcher.anyGroup());
    }

    public Collection<TriggerKey> getTriggerKeys(final Trigger.TriggerState triggerState)
            throws SchedulerException {
        final var result = new ArrayList<TriggerKey>();
        for (final var key : getAllTriggerKeys()) {
            if (scheduler.getTriggerState(key) == triggerState) {
                result.add(key);
            }
        }
        return result;
    }

    public void resetTriggerFromErrorState(final TriggerKey triggerKey) throws SchedulerException {
        scheduler.resetTriggerFromErrorState(triggerKey);
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }
}
