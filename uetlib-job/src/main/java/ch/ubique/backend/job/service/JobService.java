package ch.ubique.backend.job.service;

import static java.util.Optional.ofNullable;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.properties.JobProperties;
import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinition;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.service.core.ClassLoaderService;
import ch.ubique.backend.job.service.core.JobCreator;
import ch.ubique.backend.job.service.executor.JobExecutorService;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JobService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobDataService jobDataService;
    private final JobCreator jobCreator;
    private final ClassLoaderService classLoaderService;
    private final JobExecutorService jobExecutorService;

    public JobService(
            final JobDataService jobDataService,
            final JobCreator jobCreator,
            final ClassLoaderService classLoaderService,
            final JobExecutorService jobExecutorService) {
        this.jobDataService = jobDataService;
        this.jobCreator = jobCreator;
        this.classLoaderService = classLoaderService;
        this.jobExecutorService = jobExecutorService;

        registerAllJobs();
        jobDataService.setBusyRunsToUnfinished();
    }

    private void registerAllJobs() {
        final var reflections = new Reflections();
        for (final var cls : reflections.getTypesAnnotatedWith(RegisteredJob.class)) {
            try {
                final var jobClass = cls.asSubclass(Job.class);
                jobDataService.registerJob(jobClass);
                logger.info("Registered {}.", jobClass);
            } catch (final ClassCastException e) {
                throw new ClassCastException(
                        "Class annotated with "
                                + RegisteredJob.class
                                + " must implement "
                                + Job.class);
            }
        }

        // Try if all database entries still have a job associated with them. Remove them if not
        for (final var job : jobDataService.findJobs()) {
            try {
                getJobClass(job.canonicalClassName());
            } catch (final ClassNotFoundException e) {
                logger.warn("Could not find class for {}. Removing database entry.", job);
                jobDataService.delete(job);
            }
        }
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
        return jobCreator.createNewJobInstance(jobClass);
    }


    public int getJobIdByCanonicalClassName(final String jobClassName) {
        return jobDataService.findJobByCanonicalClassName(jobClassName).orElseThrow().id();
    }

    public int getId(final Job job) {
        return jobDataService
                .findJobByCanonicalClassName(job.getClass().getCanonicalName())
                .orElseThrow()
                .id();
    }

    public Class<? extends Job> getJobClass(final int jobId) throws ClassNotFoundException {
        return getJobClass(jobDataService.findJobById(jobId).orElseThrow().canonicalClassName());
    }

    public void startJob(
            final Class<? extends Job> jobClass, @Nullable final JobRunArguments arguments) {
        jobExecutorService.submit(jobClass, arguments, null, null);
    }

    public JobRun createNewJobRun(@NotNull final Job job) {
        final var jobId = getJobIdByCanonicalClassName(job.getClass().getCanonicalName());
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
}
