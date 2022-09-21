package ch.ubique.backend.job.scheduler;

import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerService.class);
    private final Scheduler scheduler;

    public SchedulerService(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void scheduleJob(String identifier, Job job, String cronExpression) {
        try {
            logger.info("Scheduling {} with cron: {}", identifier, cronExpression);
            JobKey key = new JobKey(identifier);

            JobDetail jobDetail = createJobDetail(key, job);

            Trigger trigger =
                    TriggerBuilder.newTrigger()
                            .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                            .build();

            scheduler.scheduleJob(jobDetail, trigger);

        } catch (SchedulerException e) {
            logger.error("Could not schedule job for: " + identifier, e);
        }
    }

    public void updateJob(String identifier, Job job, String cronExpression) {
        try {
            logger.info("Updating scheduled job {} with cron: {}", identifier, cronExpression);
            JobKey key = new JobKey(identifier);

            JobDetail jobDetail = getOrCreateJobDetail(key, job);

            removeScheduleJob(identifier);

            Trigger trigger =
                    TriggerBuilder.newTrigger()
                            .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                            .build();

            scheduler.scheduleJob(jobDetail, trigger);

        } catch (SchedulerException e) {
            logger.error("Could not update job for: " + identifier, e);
        }
    }

    public void removeScheduleJob(String identifier) {
        try {
            logger.info("Removing scheduled job {}", identifier);
            JobKey key = new JobKey(identifier);

            var triggers = scheduler.getTriggersOfJob(key);

            for (var trigger : triggers) {
                scheduler.unscheduleJob(trigger.getKey());
            }
        } catch (SchedulerException e) {
            logger.error("Could not remove job for: {}", identifier, e);
        }
    }

    public void runJobNow(String identifier, Job job, boolean forceRun) {
        try {
            logger.info("Scheduled job {} to run now", identifier);
            JobKey key = new JobKey(identifier);
            if (jobExists(key)) {
                scheduler.triggerJob(key);
            } else {
                JobDetail jobDetail = createJobDetail(key, job);
                jobDetail.getJobDataMap().put("forceRun", forceRun);
                Trigger trigger = TriggerBuilder.newTrigger().startNow().build();
                scheduler.scheduleJob(jobDetail, trigger);
            }
        } catch (SchedulerException e) {
            logger.error("Could not schedule job now: " + identifier, e);
        }
    }

    private boolean jobExists(JobKey key) throws SchedulerException {
        return scheduler.getJobDetail(key) != null;
    }

    private JobDetail createJobDetail(JobKey key, Job job) {
        return JobBuilder.newJob(job.getClass()).withIdentity(key).build();
    }

    private JobDetail getOrCreateJobDetail(JobKey key, Job job) throws SchedulerException {
        if (jobExists(key)) {
            return scheduler.getJobDetail(key);
        }
        return createJobDetail(key, job);
    }
}
