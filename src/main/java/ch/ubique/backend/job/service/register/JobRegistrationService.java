package ch.ubique.backend.job.service.register;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.annotation.RegisteredJobDefinitions;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.service.core.ClassLoaderService;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.ArrayList;

@Service
public class JobRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(JobRegistrationService.class);

    private final JobDataService jobDataService;
    private final ClassLoaderService classLoaderService;

    private final @Nullable RegisteredJobDefinitions registeredJobDefinitions;

    public JobRegistrationService(
            final JobDataService jobDataService,
            final ClassLoaderService classLoaderService,
            final Environment environment,
            @Autowired(required = false)
                    final @Nullable RegisteredJobDefinitions registeredJobDefinitions) {
        this.jobDataService = jobDataService;
        this.classLoaderService = classLoaderService;
        this.registeredJobDefinitions = registeredJobDefinitions;

        if (!environment.matchesProfiles("job-run")) {
            if (registeredJobDefinitions == null) {
                throw new IllegalStateException(
                        "RegisteredJobDefinitions bean is not available. Did you add @EnableJobs?");
            }
            registerAllJobs(registeredJobDefinitions);
        }
    }

    /** DO NOT CALL THIS METHOD WHEN PROFILE job-run IS SET, I.E., FOR DEDICATED JOBS */
    private void registerAllJobs(final RegisteredJobDefinitions registeredJobDefinitions) {
        final var registeredJobClasses = registeredJobDefinitions.registeredJobClasses();
        logger.info("Found {} annotated with {}.", registeredJobClasses, RegisteredJob.class);

        final var validJobClasses = new ArrayList<Class<? extends Job>>();
        for (final var registeredJobClass : registeredJobClasses) {
            try {
                final var jobClass = registeredJobClass.asSubclass(Job.class);
                validJobClasses.add(jobClass);
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
        jobDataService.insertNewJobs(validJobClasses);
    }

    @PostConstruct
    public void deleteUnreferencedJobs() {
        if (registeredJobDefinitions == null) {
            return;
        }

        final var registeredJobCanonicalClassnames =
                registeredJobDefinitions.registeredJobClasses().stream()
                        .map(Class::getCanonicalName)
                        .toList();

        // Check if all database entries still have a job associated with them. Remove them if not
        final var jobIdsToDelete = new ArrayList<Integer>();
        for (final var job : jobDataService.getAllJobsKnownByDatabase()) {
            try {
                final var jobClass = classLoaderService.getJobClass(job.canonicalClassName());
                if (!registeredJobCanonicalClassnames.contains(jobClass.getCanonicalName())) {
                    // The class exists but was not discovered by findAllRegisteredJobClasses()
                    // This could e.g. be the case if @EnableJobs does not include this job
                    logger.warn("{} is not part of the job classes. Removing database entry.", job);
                    jobIdsToDelete.add(job.id());
                }
            } catch (final ClassNotFoundException e) {
                logger.warn("Could not find class for {}. Removing database entry.", job);
                jobIdsToDelete.add(job.id());
            }
        }
        jobDataService.deleteJobsByIds(jobIdsToDelete);
    }
}
