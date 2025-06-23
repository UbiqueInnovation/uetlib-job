package ch.ubique.backend.job.service.register;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.annotation.RegisteredJobDefinitions;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.service.core.ClassLoaderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.text.MessageFormat;

public class JobRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(JobRegistrationService.class);

    private final JobDataService jobDataService;
    private final ClassLoaderService classLoaderService;

    public JobRegistrationService(
            final JobDataService jobDataService, final ClassLoaderService classLoaderService) {
        this.jobDataService = jobDataService;
        this.classLoaderService = classLoaderService;
    }

    /** DO NOT CALL THIS METHOD WHEN PROFILE job-run IS SET, I.E., FOR DEDICATED JOBS */
    @Transactional
    public void registerAllJobs(final RegisteredJobDefinitions registeredJobDefinitions) {
        final var registeredJobClasses = registeredJobDefinitions.registeredJobClasses();
        logger.info("Found {} annotated with {}.", registeredJobClasses, RegisteredJob.class);

        for (final var registeredJobClass : registeredJobClasses) {
            try {
                final var jobClass = registeredJobClass.asSubclass(Job.class);
                jobDataService.findOrInsertJob(jobClass);
                logger.info("Registered {}.", jobClass);
            } catch (final ClassCastException e) {
                throw new ClassCastException(
                        MessageFormat.format(
                                "Job class {0} annotated with @{1} must implement {2}",
                                registeredJobClass.getCanonicalName(),
                                RegisteredJob.class.getCanonicalName(),
                                Job.class.getCanonicalName()));
            }
        }

        // Check if all database entries still have a job associated with them. Remove them if not
        for (final var job : jobDataService.findJobs()) {
            try {
                final var jobClass = classLoaderService.getJobClass(job.canonicalClassName());
                if (!registeredJobClasses.contains(jobClass)) {
                    // The class exists but was not discovered by findAllRegisteredJobClasses()
                    // This could e.g. be the case if @EnableJobs does not include this job
                    logger.warn("{} is not part of the job classes. Removing database entry.", job);
                    jobDataService.deleteJob(job.id());
                }
            } catch (final ClassNotFoundException e) {
                logger.warn("Could not find class for {}. Removing database entry.", job);
                jobDataService.deleteJob(job.id());
            }
        }
    }
}
