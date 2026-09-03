package ch.uetlib.job.service;

import static ch.uetlib.job.model.JobRunState.CANCELLED;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import ch.uetlib.core.utils.SingleCache;
import ch.uetlib.job.Job;
import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.*;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.properties.JobProperty;
import ch.uetlib.job.model.properties.JobPropertyDefinition;
import ch.uetlib.job.model.properties.JobPropertyDefinitions;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.service.core.ClassLoaderService;
import ch.uetlib.job.service.executor.runners.JobRunner;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
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
        this.versionTag = requireNonNull(buildProperties.getVersion());
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

    public String versionTag() {
        return versionTag;
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

    /** The effective properties of a job about to be queued. */
    private JobProperties queueProperties(final JobClassAndId job) {
        return getJobProperties(
                job.id(), instantiateJob(requireNonNull(jobClassesById.get(job.id()))));
    }

    private Set<JobRunnerTaint> taintsOf(final JobProperties properties) {
        return requireNonNull(properties.get(JobPropertyDefinitions.JOB_RUN_TAINTS).value());
    }

    public JobQueuedRun queueNewRun(final JobClassAndId job, final JobRunArguments arguments) {
        final var properties = queueProperties(job);
        final var taints = taintsOf(properties);
        final var priority = properties.get(JobPropertyDefinitions.PRIORITY).value();
        final var queueBehavior =
                requireNonNull(properties.get(JobPropertyDefinitions.QUEUE_BEHAVIOR).value());
        return jobDataService.queueNewRun(
                job.id(), priority, versionTag, arguments, taints, queueBehavior);
    }

    /**
     * For a run dispatched directly, bypassing the queue's normal QUEUED/CLAIMED steps (e.g. a
     * dedicated {@code --job} CLI run): records it in the queue as already RUNNING. Idempotent - a
     * run that already has a RUNNING row (e.g. claimed through the normal queue flow before being
     * dispatched to a new pod) is left as-is rather than duplicated.
     */
    public JobQueuedRun queueRunningRun(
            final JobClassAndId job,
            final JobRunArguments arguments,
            final int jobRunId,
            final String runner) {
        final var j = requireNonNull(jobClassesById.get(job.id()));
        final var properties = getJobProperties(job.id(), instantiateJob(j));
        final var taints =
                requireNonNull(properties.get(JobPropertyDefinitions.JOB_RUN_TAINTS).value());
        return jobDataService.queueRunningRun(
                job.id(), versionTag, arguments, taints, jobRunId, runner);
    }

    /**
     * Enqueues the continuation of {@code jobRun} - its state is already computed and persisted, so
     * a poller runs only its processing phase - and points the run at {@code processingRunnerKey}
     * ({@link ch.uetlib.job.BaseStateJob#runnerForProcessing} may pick a different runner
     * than the one that computed the state). Idempotent if the run already has a {@code RUNNING}
     * queue row.
     */
    public JobQueuedRun queueReprocessingRun(
            final JobClassAndId job,
            final JobRun jobRun,
            final JobRunArguments arguments,
            final JobRunnerKey processingRunnerKey) {
        final var taints = taintsOf(queueProperties(job));
        return jobDataService.queueReprocessingRun(
                job.id(), jobRun.versionTag(), arguments, taints, jobRun.id(), processingRunnerKey);
    }

    /**
     * Proves a job run is still live. Called automatically every tick by {@code RunHeartbeat} while
     * {@code job.run(...)} is executing, based on the worker thread's own CPU progress - so most
     * jobs never need to call this themselves.
     *
     * <p>One case does need to: a job that fans work out to its own child threads (e.g. a bunch of
     * virtual threads, each doing a long-running sub-task) and then has its main thread just wait -
     * {@code Future#get}, {@code invokeAll}, a {@code StructuredTaskScope} join - makes no CPU
     * progress on the main thread for the whole wait, even though the job as a whole is working
     * fine. The automatic check-in has no visibility into those child threads, so such a job should
     * call this itself (e.g. once per completed sub-task) to keep its heartbeat honest during that
     * time.
     */
    public void touchRunningHeartbeat(final int jobRunId) {
        jobDataService.touchQueuedRunHeartbeatByJobRunId(jobRunId);
    }

    public JobRun startJobRun(final JobRun jobRun, final Instant startedAt) {
        return jobDataService.startJobRun(jobRun.id(), startedAt);
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

    public Slice<JobQueuedRun> findQueuedRunsPage(final Pageable pageable) {
        return jobDataService.findQueuedRunsPage(pageable);
    }

    public boolean claimQueuedRun(
            final JobQueuedRun jobQueuedRun, final String runnerInstanceName) {
        return jobDataService.claimQueuedRun(jobQueuedRun, runnerInstanceName);
    }

    public void setRunId(final JobQueuedRun jobQueuedRun, final int runId) {
        jobDataService.setRunId(jobQueuedRun, runId);
    }

    /** Releases a claim back to {@code QUEUED} - e.g. after a claimed run failed to schedule. */
    public void releaseQueuedRun(final JobQueuedRun jobQueuedRun) {
        jobDataService.resetQueuedRunById(jobQueuedRun.jobQueuedRunId());
    }

    public boolean hasRunner(final JobRunnerKey jobRunnerKey) {
        return applicationContext.getBeansOfType(JobRunner.class).values().stream()
                .anyMatch(runner -> runner.key() == jobRunnerKey);
    }

    public List<JobQueuedRun> findQueuedByIds(final Collection<Integer> queueRunIds) {
        return jobDataService.findQueuedRunsByIds(queueRunIds);
    }
}
