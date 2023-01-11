package ch.ubique.backend.job.scheduler;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.service.JobCreator;
import java.lang.reflect.InvocationTargetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public final class JobScheduler {

    private final SchedulerService schedulerService;
    private final JobDataService jobDataService;

    private final JobCreator jobCreator;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public JobScheduler(
            final SchedulerService schedulerService,
            final JobDataService jobDataService,
            final JobCreator jobCreator) {
        this.schedulerService = schedulerService;
        this.jobDataService = jobDataService;
        this.jobCreator = jobCreator;
    }

    public void scheduleAllRegisteredJobs()
            throws InvocationTargetException, NoSuchMethodException, InstantiationException,
                    IllegalAccessException {
        for (final var job : jobDataService.findJobs()) {
            try {
                final var instance = jobCreator.createNewJobInstance(job);
                if (instance.isCronActive() && instance.isCronExpressionValid()) {
                    schedulerService.scheduleJob(
                            job.clazzName(), instance, instance.getCronExpression());
                }
                if (instance.runOnStartup()) {
                    final var id = job.clazzName() + "_on_startup";
                    schedulerService.runJobNowManual(id, instance, false);
                }
            } catch (ClassNotFoundException e) {
                logger.warn(
                        "Could not find class for {}. Removing database entry.", job.clazzName());
                jobDataService.delete(job);
            }
        }
    }

    public void update(final Job job)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        final var instance = jobCreator.createNewJobInstance(job);
        if (!instance.isCronActive() || !instance.isCronExpressionValid()) {
            schedulerService.removeScheduleJob(job.clazzName());
            return;
        }

        schedulerService.updateJob(job.clazzName(), instance);
    }

    public void runNow(final Job job, boolean forceRun)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        schedulerService.runJobNowManual(
                job.clazzName(), jobCreator.createNewJobInstance(job), forceRun);
    }
}
