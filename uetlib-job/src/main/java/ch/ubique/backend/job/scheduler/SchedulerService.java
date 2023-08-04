package ch.ubique.backend.job.scheduler;

import ch.ubique.backend.job.Job;

import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.ZoneId;
import java.util.*;

@Service
public class SchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerService.class);
    private final Scheduler scheduler;

    public SchedulerService(final Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    private static JobDetail createJobDetail(final JobKey key, final org.quartz.Job job) {
        return JobBuilder.newJob(job.getClass()).withIdentity(key).build();
    }

    public void scheduleJob(
            final String identifier,
            final Job job,
            final String cronExpressionString,
            final ZoneId zoneId) {
        try {
            logger.info("Scheduling {} with cron expression: {}", identifier, cronExpressionString);
            final var key = new JobKey(identifier);

            final var jobDetail = createJobDetail(key, job);

            final var cronExpression = new CronExpression(cronExpressionString);
            cronExpression.setTimeZone(TimeZone.getTimeZone(zoneId));

            final var trigger =
                    TriggerBuilder.newTrigger()
                            .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                            .withPriority(job.priority())
                            .build();

            scheduler.scheduleJob(jobDetail, trigger);

        } catch (final SchedulerException | ParseException e) {
            logger.error("Could not schedule job for: {}", identifier, e);
        }
    }

    public void updateJob(final String identifier, final Job job, final ZoneId zoneId) {
        final var cronExpressionString = job.getCronExpression();
        logger.info(
                "Updating scheduled job {} with cron expression: {}",
                identifier,
                cronExpressionString);

        removeScheduleJob(identifier);
        scheduleJob(identifier, job, cronExpressionString, zoneId);
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

    public void runJobNowManual(final Job job, final boolean forceRun) {
        final var identifier = job.getClass().getCanonicalName() + "_MANUAL_" + UUID.randomUUID();
        try {
            logger.info("Manually scheduled job {} to run now. forceRun: {}", identifier, forceRun);
            final var key = new JobKey(identifier);
            final var jobDetail = createJobDetail(key, job);
            jobDetail.getJobDataMap().put("forceRun", forceRun);
            final var trigger =
                    TriggerBuilder.newTrigger().startNow().withPriority(job.priority()).build();
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (final SchedulerException e) {
            logger.error("Could not schedule job {} now. forceRun: {}", identifier, forceRun, e);
        }
    }

    private JobDetail getOrCreateJobDetail(final JobKey key, final org.quartz.Job job)
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
}
