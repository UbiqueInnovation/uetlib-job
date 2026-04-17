package ch.ubique.backend.job.service;

import static ch.ubique.backend.job.model.JobRunState.CANCELLED;

import static java.util.Optional.ofNullable;

import ch.ubique.backend.core.utils.SingleCache;
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

import com.github.benmanes.caffeine.cache.Caffeine;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@NullMarked
@DependsOn("jobRegistrationService")
public class JobService {

    private static final Logger logger = LoggerFactory.getLogger(JobService.class);

    private final String versionTag;
    private final RetryTemplate retryTemplate;
    private final JobDataService jobDataService;

    private final ApplicationContext applicationContext;

    private final Map<Integer, Class<? extends Job>> jobClassesById;
    private final Map<String, Class<? extends Job>> jobClassesByCanonicalClassName;
    private final Map<String, Integer> jobIdsByCanonicalClassName;

    private final SingleCache<Collection<String>> enabledJobsCache;

    public JobService(
            final JobDataService jobDataService,
            final ApplicationContext applicationContext,
            final ClassLoaderService classLoaderService,
            final BuildProperties buildProperties) {
        this.jobDataService = jobDataService;
        this.applicationContext = applicationContext;
        this.retryTemplate = new RetryTemplate(RetryPolicy.builder().maxRetries(9).build());
        this.versionTag = Objects.requireNonNull(buildProperties.getVersion());
        this.enabledJobsCache =
                new SingleCache<>(
                        Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).build());

        // Initialize the job maps
        jobClassesById = new HashMap<>();
        jobClassesByCanonicalClassName = new HashMap<>();
        jobIdsByCanonicalClassName = new HashMap<>();
        final var jobClassesAndIds = jobDataService.getAllJobsKnownByDatabase();
        for (final var jobClassAndId : jobClassesAndIds) {
            final var canonicalClassName = jobClassAndId.canonicalClassName();
            final var jobClass = classLoaderService.getJobClassIfExists(canonicalClassName);
            if (jobClass != null) {
                jobClassesById.put(jobClassAndId.id(), jobClass);
                jobClassesByCanonicalClassName.put(canonicalClassName, jobClass);
                jobIdsByCanonicalClassName.put(canonicalClassName, jobClassAndId.id());
            }
        }
        logger.info("Job service started with {} jobs.", jobClassesById.size());
    }

    public Class<? extends Job> getJobClassOrThrow(final JobClassAndId jobClassAndId) {
        return getJobClassOrThrow(jobClassAndId.canonicalClassName());
    }

    public Class<? extends Job> getJobClassOrThrow(final String className) {
        final var jobClass = jobClassesByCanonicalClassName.get(className);
        if (jobClass == null) {
            throw new NoSuchElementException("No such job: " + className);
        }
        return jobClass;
    }

    public Optional<Class<? extends Job>> getJobClassMaybe(final JobClassAndId jobClassAndId) {
        return getJobClassMaybe(jobClassAndId.canonicalClassName());
    }

    public Optional<Class<? extends Job>> getJobClassMaybe(final String className) {
        return ofNullable(jobClassesByCanonicalClassName.get(className));
    }

    public JobProperties getProperties(final JobClassAndId jobClassAndId) {
        final var job = instantiateJob(getJobClassOrThrow(jobClassAndId));
        return getJobProperties(jobClassAndId.id(), job);
    }

    public JobProperties getProperties(final Job job) {
        return getJobProperties(findJobIdOrThrow(job.getClass()), job);
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
        return createNewInstance(jobClass);
    }

    public Optional<Integer> findJobId(final Class<? extends Job> jobClass) {
        return findJobId(jobClass.getCanonicalName());
    }

    public Optional<Integer> findJobId(final String canonicalClassName) {
        return ofNullable(jobIdsByCanonicalClassName.get(canonicalClassName));
    }

    public int findJobIdOrThrow(final Class<? extends Job> jobClass) {
        return findJobIdOrThrow(jobClass.getCanonicalName());
    }

    public int findJobIdOrThrow(final String canonicalClassName) {
        return findJobId(canonicalClassName)
                .orElseThrow(
                        () -> new NoSuchElementException("No such job: " + canonicalClassName));
    }

    public JobClassAndId jobClassAndIdOrThrow(final Class<? extends Job> jobClass) {
        return new JobClassAndId(findJobIdOrThrow(jobClass), jobClass);
    }

    public Optional<JobClassAndId> findJobById(final int jobId) {
        return ofNullable(jobClassesById.get(jobId)).map(job -> new JobClassAndId(jobId, job));
    }

    public JobClassAndId findJobByIdOrThrow(final int jobId) {
        return findJobById(jobId)
                .orElseThrow(() -> new NoSuchElementException("No job found for id " + jobId));
    }

    private JobRun createNewJobRun(
            final Job job, final JobRunnerKey runnerKey, final JobRunArguments arguments) {
        final var jobId = findJobIdOrThrow(job.getClass());
        return jobDataService.createJobRun(jobId, versionTag, runnerKey, arguments);
    }

    public JobRun getOrCreateJobRun(
            final Job job,
            @Nullable final Integer runId,
            final JobRunnerKey runnerKey,
            final JobRunArguments arguments) {
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

    public JobRun startJobRun(final JobRun jobRun, final Instant startedAt) {
        return jobDataService.startJobRun(jobRun.id(), JobRunState.BUSY, startedAt);
    }

    public void updateJobRunState(
            final JobRun jobRun, final JobRunState state, final Instant transitionedAt) {
        retryTemplate.invoke(
                () -> jobDataService.updateJobRunState(jobRun.id(), state, transitionedAt));

        logger.debug(
                "Job run {} transitioned to state {} at time {}.",
                jobRun.id(),
                state,
                transitionedAt);
    }

    public Collection<Job> getAllJobs() {
        return jobClassesById.values().stream().map(this::instantiateJob).toList();
    }

    public Collection<Class<? extends Job>> getAllJobClasses() {
        return jobClassesById.values();
    }

    public Collection<JobClassAndId> getAllJobClassesWithId() {
        return jobClassesById.values().stream().map(this::jobClassAndIdOrThrow).toList();
    }

    public Collection<JobClassAndId> getEnabledJobClassesWithId() {
        final var enabled = enabledJobsCache.get(this::getEnabledJobsCanonicalNames);
        return jobClassesById.values().stream()
                .map(this::jobClassAndIdOrThrow)
                .filter(j -> enabled.contains(j.canonicalClassName()))
                .toList();
    }

    public boolean cancel(final JobRun jobRun) {
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

    private JobRunner jobRunner(final JobRun jobRun) {
        return applicationContext.getBeansOfType(JobRunner.class).values().stream()
                .filter(runner -> runner.key() == jobRun.runnerKey())
                .findFirst()
                .orElseThrow();
    }

    public Collection<JobRun> findJobRunsByStates(final Collection<JobRunState> states) {
        return jobDataService.findJobRunByStates(states);
    }

    public boolean isEnabled(final String jobCanonicalClassName) {
        return enabledJobsCache
                .get(this::getEnabledJobsCanonicalNames)
                .contains(jobCanonicalClassName);
    }

    private Collection<String> getEnabledJobsCanonicalNames() {
        return jobDataService.getEnabledJobs().stream()
                .map(JobClassAndId::canonicalClassName)
                .toList();
    }

    public boolean exists(final String jobCanonicalClassName) {
        return jobIdsByCanonicalClassName.containsKey(jobCanonicalClassName);
    }

    public void setEnabled(final Class<? extends Job> jobClass, final boolean isEnabled) {
        jobDataService.setEnabled(findJobIdOrThrow(jobClass), isEnabled);
    }

    private Job createNewInstance(final Class<? extends Job> jobClass) {
        return (Job)
                applicationContext
                        .getAutowireCapableBeanFactory()
                        .autowire(jobClass, AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, true);
    }
}
