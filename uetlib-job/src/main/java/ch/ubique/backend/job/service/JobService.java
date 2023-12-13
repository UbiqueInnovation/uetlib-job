package ch.ubique.backend.job.service;

import static java.util.Optional.ofNullable;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.annotation.EnableJobs;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobProperties;
import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinition;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.core.ClassLoaderService;
import ch.ubique.backend.storage.annotations.EnableAnnotationUtils;

import com.pivovarit.function.ThrowingFunction;
import com.pivovarit.function.ThrowingPredicate;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JobService {

    private static final Logger logger = LoggerFactory.getLogger(JobService.class);

    private final JobDataService jobDataService;
    private final ApplicationContext applicationContext;
    private final ClassLoaderService classLoaderService;
    private final Environment environment;

    public JobService(
            final JobDataService jobDataService,
            final ApplicationContext applicationContext,
            final ClassLoaderService classLoaderService,
            final Environment environment) {
        this.jobDataService = jobDataService;
        this.applicationContext = applicationContext;
        this.classLoaderService = classLoaderService;
        this.environment = environment;
    }

    @PostConstruct
    void postConstruct() {
        if (environment.matchesProfiles("!job-run")) {
            registerAllJobs();
            setAbortedRunsToUnfinished();
        }
    }

    private Set<Class<?>> findAllRegisteredJobClasses() {
        final var mergedAnnotationsSearch =
                MergedAnnotations.search(MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                        .withEnclosingClasses(
                                clazz ->
                                        !ClassUtils.isInnerClass(clazz)
                                                && !ClassUtils.isStaticClass(clazz));
        final var enableJobsClass =
                EnableAnnotationUtils.findSingletonEnableClass(
                        applicationContext, EnableJobs.class);

        final var enableJobsAnnotation =
                mergedAnnotationsSearch.from(enableJobsClass).get(EnableJobs.class).synthesize();
        return EnableAnnotationUtils.findAllClassesBy(
                enableJobsAnnotation.jobs(),
                enableJobsAnnotation.basePackages(),
                enableJobsAnnotation.basePackageClasses(),
                enableJobsClass.getPackageName(),
                RegisteredJob.class);
    }

    private void registerAllJobs() {
        final var jobClasses = findAllRegisteredJobClasses();
        logger.info("Found {} annotated with {}.", jobClasses, RegisteredJob.class);

        for (final var cls : jobClasses) {
            try {
                final var jobClass = cls.asSubclass(Job.class);
                jobDataService.findOrInsertJob(jobClass);
                logger.info("Registered {}.", jobClass);
            } catch (final ClassCastException e) {
                throw new ClassCastException(
                        "Class annotated with "
                                + RegisteredJob.class
                                + " must implement "
                                + Job.class);
            }
        }

        // Check if all database entries still have a job associated with them. Remove them if not
        for (final var job : jobDataService.findJobs()) {
            try {
                final var jobClass = getJobClass(job.canonicalClassName());
                if (!jobClasses.contains(jobClass)) {
                    // The class exists, but was not discovered by findAllRegisteredJobClasses()
                    // This could e.g. be the case if @EnableJobs does not include this job
                    logger.warn("{} is not part of the job classes. Removing database entry.", job);
                    jobDataService.delete(job);
                }
            } catch (final ClassNotFoundException e) {
                logger.warn("Could not find class for {}. Removing database entry.", job);
                jobDataService.delete(job);
            }
        }
    }

    private void setAbortedRunsToUnfinished() {
        final var now = Instant.now();
        jobDataService.findJobRunsWithoutFinishedTimestamp().stream()
                .filter(ThrowingPredicate.unchecked(this::usesCurrentRunner))
                .map(
                        jobRun ->
                                jobRun.modify()
                                        .finishedAt(now)
                                        .state(JobRunState.UNFINISHED)
                                        .toJobRun())
                .forEach(jobDataService::saveJobRun);
    }

    private boolean usesCurrentRunner(final JobRun jobRun) throws ClassNotFoundException {
        return getProperties(instantiateJob(getJobClass(jobRun.jobId())))
                        .get(JobPropertyDefinitions.RUNNER)
                        .value()
                == JobRunnerKey.CURRENT;
    }

    public Class<? extends Job> getJobClass(final String className) throws ClassNotFoundException {
        return classLoaderService.getClassForName(className, Job.class);
    }

    /**
     * This method computes the effective job properties for a given job and works as following:
     * <li>If the job has fixed properties -> return them
     * <li>Otherwise: for each job property definition
     *
     *     <ul>
     *       <li>If this definition has a db value -> return this value
     *       <li>Otherwise return initial value (if present) or empty
     *     </ul>
     */
    public JobProperties getProperties(final Job job) {
        if (job.propertiesConfig().isFixed()) {
            return new JobProperties(job.propertiesConfig().properties());
        }

        // The code below merges db properties with initial properties.
        final var db =
                jobDataService.getProperties(getId(job)).stream()
                        .collect(Collectors.toMap(JobProperty::key, Function.identity()));

        final var initial =
                job.propertiesConfig().properties().stream()
                        .collect(Collectors.toMap(JobProperty::key, Function.identity()));

        return new JobProperties(
                JobPropertyDefinitions.values().stream()
                        .map(JobPropertyDefinition::key)
                        .map(key -> ofNullable(db.get(key)).or(() -> ofNullable(initial.get(key))))
                        .flatMap(Optional::stream)
                        .toList());
    }

    public Job instantiateJob(final Class<? extends Job> jobClass) {
        return (Job)
                applicationContext
                        .getAutowireCapableBeanFactory()
                        .autowire(jobClass, AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, true);
    }

    public Job instantiateJob(final JobClassAndId jobClassAndId) throws ClassNotFoundException {
        return instantiateJob(getJobClass(jobClassAndId.id()));
    }

    public int getJobId(final Class<? extends Job> jobClass) {
        return jobDataService.findOrInsertJob(jobClass).id();
    }

    public int getId(final Job job) {
        return getJobId(job.getClass());
    }

    public Class<? extends Job> getJobClass(final int jobId) throws ClassNotFoundException {
        return getJobClass(jobDataService.findJobById(jobId).orElseThrow().canonicalClassName());
    }

    public JobRun createNewJobRun(@NotNull final Job job) {
        final var jobId = getJobId(job.getClass());
        return jobDataService.createJobRun(jobId, job.version());
    }

    public JobRun getOrCreateJobRun(@NotNull final Job job, @Nullable final Integer runId) {
        return ofNullable(runId)
                .flatMap(jobDataService::findJobRunById)
                .orElseGet(() -> createNewJobRun(job));
    }

    public void updateJobRun(@NotNull final JobRun jobRun) {
        jobDataService.saveJobRun(jobRun);
    }

    public Collection<Job> getAllJobs() {
        return jobDataService.findJobs().stream()
                .map(ThrowingFunction.lifted(this::instantiateJob))
                .flatMap(Optional::stream)
                .toList();
    }
}
