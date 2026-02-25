package ch.ubique.backend.job.service;

import static ch.ubique.backend.job.model.JobRunState.CANCELLED;

import static java.util.Optional.ofNullable;

import ch.ubique.backend.job.Job;
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

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JobService {

    private static final Logger logger = LoggerFactory.getLogger(JobService.class);

    private final String versionTag;
    private final RetryTemplate retryTemplate;
    private final JobDataService jobDataService;
    private final ApplicationContext applicationContext;
    private final ClassLoaderService classLoaderService;

    public JobService(
            final JobDataService jobDataService,
            final ApplicationContext applicationContext,
            final ClassLoaderService classLoaderService,
            final BuildProperties buildProperties) {
        this.jobDataService = jobDataService;
        this.applicationContext = applicationContext;
        this.classLoaderService = classLoaderService;
        this.retryTemplate = new RetryTemplate(RetryPolicy.builder().maxRetries(9).build());
        this.versionTag = buildProperties.getVersion();
    }

    public Class<? extends Job> getJobClassOrThrow(final JobClassAndId jobClassAndId)
            throws ClassNotFoundException {
        return classLoaderService.getJobClass(jobClassAndId.canonicalClassName());
    }

    public Class<? extends Job> getJobClassOrThrow(final String className)
            throws ClassNotFoundException {
        return classLoaderService.getJobClass(className);
    }

    public Optional<Class<? extends Job>> getJobClassMaybe(final JobClassAndId jobClassAndId) {
        return getJobClassMaybe(jobClassAndId.canonicalClassName());
    }

    public Optional<Class<? extends Job>> getJobClassMaybe(final String className) {
        try {
            return Optional.of(getJobClassOrThrow(className));
        } catch (final ClassNotFoundException e) {
            logger.info("Class not found for  {}.", className);
            return Optional.empty();
        }
    }

    public JobProperties getProperties(final JobClassAndId jobClassAndId)
            throws ClassNotFoundException {
        final var job = instantiateJob(getJobClassOrThrow(jobClassAndId));
        return getJobProperties(jobClassAndId.id(), job);
    }

    public JobProperties getProperties(final Job job) {
        return getJobProperties(findOrInsertJobByClass(job.getClass()).id(), job);
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
    private JobProperties getJobProperties(final int jobId, final Job job) {
        if (job.propertiesConfig().isFixed()) {
            return new JobProperties(job.propertiesConfig().properties());
        }

        // The code below merges db properties with initial properties.
        final var db =
                jobDataService.getProperties(jobId).stream()
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

    public JobClassAndId findOrInsertJobByClass(final Class<? extends Job> jobClass) {
        return jobDataService.findOrInsertJob(jobClass);
    }

    public Optional<JobClassAndId> findJobById(final int jobId) {
        final var jobMaybe = jobDataService.findJobById(jobId);
        if (jobMaybe.isEmpty()) {
            logger.info("No job found for job id {}.", jobId);
        }
        return jobMaybe;
    }

    public JobClassAndId findJobByIdOrThrow(final int jobId) {
        return jobDataService
                .findJobById(jobId)
                .orElseThrow(() -> new NoSuchElementException("No job found for id " + jobId));
    }

    private JobRun createNewJobRun(
            final @NotNull Job job,
            final @NotNull JobRunnerKey runnerKey,
            final @NotNull JobRunArguments arguments) {
        final var jobId = findOrInsertJobByClass(job.getClass()).id();
        return jobDataService.createJobRun(jobId, versionTag, runnerKey, arguments);
    }

    public JobRun getOrCreateJobRun(
            final @NotNull Job job,
            @Nullable final Integer runId,
            final @NotNull JobRunnerKey runnerKey,
            final @NotNull JobRunArguments arguments) {
        return ofNullable(runId)
                .flatMap(jobDataService::findJobRunById)
                .map(jobRun -> setRunnerKeyAndArguments(jobRun, runnerKey, arguments))
                .orElseGet(() -> createNewJobRun(job, runnerKey, arguments));
    }

    private JobRun setRunnerKeyAndArguments(
            final JobRun jobRun, final JobRunnerKey runnerKey, final JobRunArguments arguments) {
        if (jobRun.runnerKey() == runnerKey
                && Objects.equals(arguments, jobRun.jobRunArguments())) {
            return jobRun;
        }
        return jobDataService.updateJobRunRunnerKeyAndArguments(jobRun, runnerKey, arguments);
    }

    public Optional<JobRun> getJobRunMaybe(final int runId) {
        return jobDataService.findJobRunById(runId);
    }

    public JobRun getJobRun(final int runId) {
        return getJobRunMaybe(runId).orElseThrow();
    }

    public Optional<JobRun> getLastJobRun(final int jobId) {
        return jobDataService.findLatestJobRun(jobId);
    }

    public JobRun startJobRun(final @NotNull JobRun jobRun, final @NotNull Instant startedAt) {
        return jobDataService.startJobRun(jobRun.id(), JobRunState.BUSY, startedAt);
    }

    public void updateJobRunState(
            final @NotNull JobRun jobRun,
            final @NotNull JobRunState state,
            final @NotNull Instant transitionedAt) {
        try {
            retryTemplate.execute(
                    () -> {
                        jobDataService.updateJobRunState(jobRun.id(), state, transitionedAt);
                        return null;
                    });
        } catch (final RetryException e) {
            logger.warn("Could not update job run state for {} with retries.", jobRun, e);
            throw new RuntimeException(e.getCause());
        }

        logger.debug(
                "Job run {} transitioned to state {} at time {}.",
                jobRun.id(),
                state,
                transitionedAt);
    }

    public Collection<Job> getAllJobs() {
        return jobDataService.findJobs().stream()
                .map(this::getJobClassMaybe)
                .flatMap(Optional::stream)
                .map(this::instantiateJob)
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
        updateJobRunState(jobRun, CANCELLED, Instant.now());
        return true;
    }

    private JobRunner jobRunner(final @NotNull JobRun jobRun) {
        return applicationContext.getBeansOfType(JobRunner.class).values().stream()
                .filter(runner -> runner.key() == jobRun.runnerKey())
                .findFirst()
                .orElseThrow();
    }

    public Collection<JobRun> findJobRunsByStates(final Collection<JobRunState> states) {
        return jobDataService.findJobRunByStates(states);
    }

    public boolean isEnabled(final Class<? extends Job> jobClass) {
        return jobDataService.isEnabled(jobClass);
    }

    public boolean exists(final Class<? extends Job> jobClass) {
        return jobDataService.exists(jobClass);
    }

    public void setEnabled(final Class<? extends Job> jobClass, final boolean isEnabled) {
        jobDataService.setEnabled(jobClass, isEnabled);
    }
}
