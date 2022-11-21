package ch.ubique.backend.job.scheduler;

import ch.ubique.backend.job.BaseJob;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerService.class);
    private final Scheduler scheduler;

    public SchedulerService(final Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void scheduleJob(final String identifier, final Job job, final String cronExpression) {
        try {
            logger.info("Scheduling {} with cron expression: {}", identifier, cronExpression);
            final var key = new JobKey(identifier);

            final var jobDetail = createJobDetail(key, job);

            final var trigger =
                    TriggerBuilder.newTrigger()
                            .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                            .build();

            scheduler.scheduleJob(jobDetail, trigger);

        } catch (SchedulerException e) {
            logger.error("Could not schedule job for: " + identifier, e);
        }
    }

    public void updateJob(final String identifier, final BaseJob job) {
        final var cronExpression = job.getCronExpression();
        try {
            logger.info(
                    "Updating scheduled job {} with cron expression: {}",
                    identifier,
                    cronExpression);
            final var key = new JobKey(identifier);
            final var jobDetail = getOrCreateJobDetail(key, job);

            removeScheduleJob(identifier);

            final var trigger =
                    TriggerBuilder.newTrigger()
                            .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                            .build();
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException e) {
            logger.error("Could not update job for: " + identifier, e);
        }
    }

    public void removeScheduleJob(final String identifier) {
        try {
            logger.info("Removing scheduled job {}", identifier);
            final var key = new JobKey(identifier);

            var triggers = scheduler.getTriggersOfJob(key);

            for (var trigger : triggers) {
                scheduler.unscheduleJob(trigger.getKey());
            }
        } catch (SchedulerException e) {
            logger.error("Could not remove job {}", identifier, e);
        }
    }

    public void runJobNowManual(final String identifier, final Job job, final boolean forceRun) {
        final var manualIdentifier = identifier + "_MANUAL";
        try {
            logger.info(
                    "Manually scheduled job {} to run now. forceRun: {}",
                    manualIdentifier,
                    forceRun);
            final var key = new JobKey(manualIdentifier);
            final var jobDetail = createJobDetail(key, job);
            jobDetail.getJobDataMap().put("forceRun", forceRun);
            final var trigger = TriggerBuilder.newTrigger().startNow().build();
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException e) {
            logger.error(
                    "Could not schedule job {} now. forceRun: {}", manualIdentifier, forceRun, e);
        }
    }

    private static JobDetail createJobDetail(final JobKey key, final Job job) {
        return JobBuilder.newJob(job.getClass()).withIdentity(key).build();
    }

    private JobDetail getOrCreateJobDetail(JobKey key, Job job) throws SchedulerException {
        return Optional.ofNullable(scheduler.getJobDetail(key))
                .orElseGet(() -> createJobDetail(key, job));
    }
}
