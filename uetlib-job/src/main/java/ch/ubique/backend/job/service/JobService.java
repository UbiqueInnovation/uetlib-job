package ch.ubique.backend.job.service;

import static ch.ubique.backend.job.model.JobRunState.UNFINISHED;

import static java.util.Optional.ofNullable;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.annotation.EnableJobs;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobProperties;
import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinition;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.core.ClassLoaderService;
import ch.ubique.backend.job.service.executor.runners.JobRunner;
import ch.ubique.backend.storage.annotations.EnableAnnotationUtils;

import com.pivovarit.function.ThrowingFunction;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /** DO NOT CALL THIS METHOD WHEN PROFILE job-run IS SET, I.E. FOR DEDICATED JOBS */
    @Transactional
    public void init() {
        registerAllJobs();
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

        final var storageClasses =
                EnableAnnotationUtils.findAllClassesBy(
                        enableJobsAnnotation.jobs(),
                        enableJobsAnnotation.basePackages(),
                        enableJobsAnnotation.basePackageClasses(),
                        enableJobsClass.getPackageName(),
                        RegisteredJob.class);

        return EnableAnnotationUtils.filterClassesByProfileEnabled(
                environment, mergedAnnotationsSearch, storageClasses);
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
                        MessageFormat.format(
                                "Job class {0} annotated with @{1} must implement {2}",
                                cls.getCanonicalName(),
                                RegisteredJob.class.getCanonicalName(),
                                Job.class));
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
                    jobDataService.deleteJob(job.id());
                }
            } catch (final ClassNotFoundException e) {
                logger.warn("Could not find class for {}. Removing database entry.", job);
                jobDataService.deleteJob(job.id());
            }
        }
    }

    public Class<? extends Job> getJobClass(final String className) throws ClassNotFoundException {
        return classLoaderService.getClassForName(className, Job.class);
    }

    public JobProperties getProperties(final JobClassAndId jobClassAndId)
            throws ClassNotFoundException {
        final var job = instantiateJob(jobClassAndId);
        return getProperties(job);
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
        return instantiateJob(jobClassAndId.id());
    }

    public Job instantiateJob(final int jobId) throws ClassNotFoundException {
        return instantiateJob(getJobClass(jobId));
    }

    public int getJobId(final Class<? extends Job> jobClass) {
        return jobDataService.findOrInsertJob(jobClass).id();
    }

    public int getId(final Job job) {
        return getJobId(job.getClass());
    }

    public JobClassAndId getJobClassAndId(final Job job) {
        return new JobClassAndId(getId(job), job.getClass().getCanonicalName());
    }

    public Class<? extends Job> getJobClass(final int jobId) throws ClassNotFoundException {
        return getJobClass(jobDataService.findJobById(jobId).orElseThrow().canonicalClassName());
    }

    private JobRun createNewJobRun(
            final @NotNull Job job,
            final @NotNull JobRunnerKey runnerKey,
            final @NotNull JobRunArguments arguments) {
        final var jobId = getJobId(job.getClass());
        return jobDataService.createJobRun(jobId, job.version(), runnerKey, arguments);
    }

    public JobRun getOrCreateJobRun(
            final @NotNull Job job,
            @Nullable final Integer runId,
            final @NotNull JobRunnerKey runnerKey,
            final @NotNull JobRunArguments arguments) {
        return ofNullable(runId)
                .flatMap(jobDataService::findJobRunById)
                .map(jobRun -> updateJobIfNeeded(jobRun, runnerKey, arguments))
                .orElseGet(() -> createNewJobRun(job, runnerKey, arguments));
    }

    private JobRun updateJobIfNeeded(
            final JobRun jobRun, final JobRunnerKey runnerKey, final JobRunArguments arguments) {
        if (jobRun.runnerKey() == runnerKey
                && Objects.equals(arguments, jobRun.jobRunArguments())) {
            return jobRun;
        }
        return jobDataService.updateJobRun(jobRun, runnerKey, arguments);
    }

    public JobRun getJobRun(final int runId) {
        return jobDataService.findJobRunById(runId).orElseThrow();
    }

    public Optional<JobRun> getLastJobRun(final int jobId) {
        return jobDataService.findLatestJobRun(jobId);
    }

    public JobRun startJobRun(final @NotNull JobRun jobRun, final @NotNull Instant startedAt) {
        return jobDataService.startJobRun(jobRun.id(), JobRunState.BUSY, startedAt);
    }

    public void finishJobRun(
            final @NotNull JobRun jobRun,
            final @NotNull JobRunState finalState,
            final @NotNull Instant finishedAt) {
        jobDataService.finishJobRun(jobRun.id(), finalState, finishedAt);
    }

    public void updateJobRunState(
            final @NotNull JobRun jobRun,
            final @NotNull JobRunState state,
            final @NotNull Instant transitionedAt) {
        jobDataService.updateJobRunState(jobRun.id(), state, transitionedAt);
    }

    public Collection<Job> getAllJobs() {
        return jobDataService.findJobs().stream()
                .map(ThrowingFunction.lifted(this::instantiateJob))
                .flatMap(Optional::stream)
                .toList();
    }

    public boolean cancel(final @NotNull JobRun jobRun) {
        if (!jobRun.latestState().isRunning()) {
            logger.info("Job run can not be canceled, as it is not running.");
            return false;
        }
        final var jobRunner = jobRunner(jobRun);
        logger.info("Requesting {} to cancel job run {}.", jobRunner, jobRun);
        jobRunner.cancel(jobRun);
        jobDataService.finishJobRun(jobRun.id(), UNFINISHED, Instant.now());
        return true;
    }

    private JobRunner jobRunner(final @NotNull JobRun jobRun) {
        return applicationContext.getBeansOfType(JobRunner.class).values().stream()
                .filter(runner -> runner.key() == jobRun.runnerKey())
                .findFirst()
                .orElseThrow();
    }
}
