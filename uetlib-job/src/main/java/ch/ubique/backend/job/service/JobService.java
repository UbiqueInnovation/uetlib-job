package ch.ubique.backend.job.service;

import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.scheduler.SchedulerService;

import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

@Service
public class JobService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobDataService jobDataService;
    private final SchedulerService schedulerService;
    private final JobCreator jobCreator;
    private final ZoneId defaultZoneId;

    public JobService(
            final JobDataService jobDataService,
            final SchedulerService schedulerService,
            final JobCreator jobCreator,
            @Value("${ch.ubique.backend.job.cron.timezone:UTC}") final String defaultZoneId) {
        this.jobDataService = jobDataService;
        this.schedulerService = schedulerService;
        this.jobCreator = jobCreator;
        this.defaultZoneId = ZoneId.of(defaultZoneId);

        registerAllJobs();
        scheduleAllRegisteredJobs();
        jobDataService.setBusyRunsToUnfinished();
    }

    private void registerAllJobs() {
        final var reflections = new Reflections();
        for (final var cls : reflections.getTypesAnnotatedWith(RegisteredJob.class)) {
            try {
                final var job = jobCreator.createNewJobInstance(cls.getCanonicalName());
                final var properties = job.getProperties();
                logger.info("Registered {}. Properties = {}", job, properties);
            } catch (final ClassNotFoundException e) {
                logger.error("Reflections discovered a class that can not be found.", e);
            }
        }
    }

    public void scheduleAllRegisteredJobs() {
        for (final var job : jobDataService.findJobs()) {
            try {
                final var instance = jobCreator.createNewJobInstance(job.clazzName());
                if (instance.isCronActive() && instance.isCronExpressionValid()) {
                    schedulerService.scheduleJob(
                            job.clazzName(), instance, instance.getCronExpression(), defaultZoneId);
                }
                if (instance.runOnStartup()) {
                    schedulerService.runJobNowManual(instance, false);
                }
            } catch (final ClassNotFoundException e) {
                logger.warn(
                        "Could not find class for {}. Removing database entry.", job.clazzName());
                jobDataService.delete(job);
            }
        }
    }

    public void startJob(final String className, final boolean forceRun)
            throws ClassNotFoundException {
        schedulerService.runJobNowManual(jobCreator.createNewJobInstance(className), forceRun);
    }

    public ZoneId getDefaultZoneId() {
        return defaultZoneId;
    }
}
