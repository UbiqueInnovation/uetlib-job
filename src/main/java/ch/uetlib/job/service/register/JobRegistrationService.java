package ch.uetlib.job.service.register;

import ch.uetlib.job.Job;
import ch.uetlib.job.RegisteredJob;
import ch.uetlib.job.annotation.RegisteredJobDefinitions;
import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.service.core.ClassLoaderService;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.ArrayList;

@Service
public class JobRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(JobRegistrationService.class);

    private final JobDataService jobDataService;
    private final ClassLoaderService classLoaderService;
    private final ApplicationContext applicationContext;

    private final @Nullable RegisteredJobDefinitions registeredJobDefinitions;

    public JobRegistrationService(
            final JobDataService jobDataService,
            final ClassLoaderService classLoaderService,
            final ApplicationContext applicationContext,
            final Environment environment,
            @Autowired(required = false)
                    final @Nullable RegisteredJobDefinitions registeredJobDefinitions) {
        this.jobDataService = jobDataService;
        this.classLoaderService = classLoaderService;
        this.applicationContext = applicationContext;
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
        jobDataService.insertNewJobs(validJobClasses, this::resolvePriority);
    }

    /**
     * Best-effort: this runs during this bean's own construction, very early in context startup, so
     * autowiring a job that itself (transitively) depends on a bean not yet created here can fail
     * (e.g. a job depending on JobService, which is @DependsOn this very bean). Falling back to 0
     * is safe - JobPriorityService reconciles every job's priority again once the application is
     * fully up, so a job whose priority couldn't be resolved yet here is corrected shortly after.
     */
    private int resolvePriority(final Class<? extends Job> jobClass) {
        try {
            final var instance =
                    (Job)
                            applicationContext
                                    .getAutowireCapableBeanFactory()
                                    .autowire(
                                            jobClass,
                                            AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR,
                                            true);
            return instance.priority();
        } catch (final Exception e) {
            logger.warn(
                    "Could not instantiate {} to resolve its priority at registration time;"
                            + " defaulting to 0 for now, JobPriorityService will correct this once"
                            + " the application is ready.",
                    jobClass,
                    e);
            return 0;
        }
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
