package ch.uetlib.job.data.service;

import static ch.uetlib.job.model.queue.JobRunQueueState.CANCELLED;
import static ch.uetlib.job.model.queue.JobRunQueueState.CLAIMED;
import static ch.uetlib.job.model.queue.JobRunQueueState.QUEUED;
import static ch.uetlib.job.model.queue.JobRunQueueState.RUNNING;

import static java.util.Objects.requireNonNull;

import ch.uetlib.core.exception.ResourceNotFoundException;
import ch.uetlib.core.utils.MapCollectors;
import ch.uetlib.job.Job;
import ch.uetlib.job.data.repository.*;
import ch.uetlib.job.logging.JobRunLoggingEvent;
import ch.uetlib.job.logging.LoggingEvent;
import ch.uetlib.job.model.*;
import ch.uetlib.job.model.entity.*;
import ch.uetlib.job.model.input.JobRunInputDeclaration;
import ch.uetlib.job.model.properties.*;
import ch.uetlib.job.model.properties.enums.JobQueueBehavior;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.model.queue.QueueNewRunResult;
import ch.uetlib.job.model.runner.conditions.JobRunnerTaint;
import ch.uetlib.job.model.triggers.*;
import ch.uetlib.job.model.triggers.TriggerDefinition;
import ch.uetlib.job.service.triggers.TriggerCoordinatorService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Range;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class JobDataService {

    private final JobRepository jobRepository;
    private final JobTriggerRepository jobTriggerRepository;
    private final JobPropertyRepository jobPropertyRepository;
    private final JobRunRepository jobRunRepository;
    private final JobRunStateTransitionRepository jobRunStateTransitionRepository;
    private final JobQueuedRunRepository jobQueuedRunRepository;
    private final JobDaemonRepository jobDaemonRepository;
    private final JobRunInputRepository jobRunInputRepository;
    private final JobRunDatabaseSubmissionRepository jobRunDatabaseSubmissionRepository;
    private final K8sJobRepository k8sJobRepository;
    private final LoggingEventRepository loggingEventRepository;
    private final JdbcTemplate jobJdbcTemplate;

    @SuppressWarnings("NullAway.Init")
    @PersistenceContext
    private EntityManager entityManager;

    public JobDataService(
            final JobRepository jobRepository,
            final JobTriggerRepository jobTriggerRepository,
            final JobPropertyRepository jobPropertyRepository,
            final JobRunRepository jobRunRepository,
            final JobRunStateTransitionRepository jobRunStateTransitionRepository,
            final JobRunInputRepository jobRunInputRepository,
            final JobRunDatabaseSubmissionRepository jobRunDatabaseSubmissionRepository,
            final JobQueuedRunRepository jobQueuedRunRepository,
            final JobDaemonRepository jobDaemonRepository,
            final K8sJobRepository k8sJobRepository,
            final LoggingEventRepository loggingEventRepository,
            final JdbcTemplate jobJdbcTemplate) {
        this.jobRepository = jobRepository;
        this.jobTriggerRepository = jobTriggerRepository;
        this.jobPropertyRepository = jobPropertyRepository;
        this.jobRunRepository = jobRunRepository;
        this.jobRunStateTransitionRepository = jobRunStateTransitionRepository;
        this.jobRunInputRepository = jobRunInputRepository;
        this.jobRunDatabaseSubmissionRepository = jobRunDatabaseSubmissionRepository;
        this.jobQueuedRunRepository = jobQueuedRunRepository;
        this.jobDaemonRepository = jobDaemonRepository;
        this.k8sJobRepository = k8sJobRepository;
        this.loggingEventRepository = loggingEventRepository;
        this.jobJdbcTemplate = jobJdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Optional<JobRun> findLatestJobRun(final int jobId) {
        return jobRunRepository
                .findFirstByJobIdOrderByStartedAtDesc(jobId)
                .map(JobRunEntity::toJobRun);
    }

    @Transactional(readOnly = true)
    public Collection<JobRun> findAllLatestRuns(final Collection<Integer> jobIds) {
        return jobRunRepository.findLatestRuns(jobIds).stream()
                .map(JobLatestRunEntity::toJobRun)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<JobRun> findLatestJobRunByState(final int jobId, final JobRunState state) {
        return jobRunRepository
                .findFirstByJobIdAndLatestStateOrderByStartedAtDesc(jobId, state)
                .map(JobRunEntity::toJobRun);
    }

    @Transactional(readOnly = true)
    public Optional<JobRun> findAnyJobRunInStatesSince(
            final int jobId, final Collection<JobRunState> states, final Instant after) {
        if (states.isEmpty()) {
            return Optional.empty();
        }
        return jobRunRepository
                .findAnyJobRunInStateSince(jobId, states, after)
                .map(JobRunEntity::toJobRun);
    }

    /** Find distinct job run states for the given jobs */
    @Transactional(readOnly = true)
    public Map<Integer, Set<JobRunState>> findJobRunStates(final Collection<Integer> jobs) {
        if (jobs.isEmpty()) {
            return Collections.emptyMap();
        }
        return jobRunRepository.findJobRunStates(jobs);
    }

    /** Find distinct job run states for jobs started after the given per-job instances */
    @Transactional(readOnly = true)
    public Map<Integer, Set<JobRunState>> findJobRunStatesSince(final Map<Integer, Instant> jobs) {
        if (jobs.isEmpty()) {
            return Collections.emptyMap();
        }
        return jobRunRepository.findJobRunStatesSince(jobs);
    }

    @Transactional(readOnly = true)
    public Collection<JobRun> findLatestFinishedJobRuns(final Collection<Integer> jobIds) {
        return jobRunRepository.findLatestFinishedRuns(jobIds).stream()
                .map(JobLatestFinishedRunEntity::toJobRun)
                .toList();
    }

    public Collection<JobRun> findJobRunsInState(
            final Set<Integer> jobIds, final JobRunState jobRunState) {
        return jobRunRepository.findAllByJobIdInAndLatestState(jobIds, jobRunState).stream()
                .map(JobRunEntity::toJobRun)
                .toList();
    }

    @Transactional(readOnly = true)
    public Collection<JobRun> findJobRunByStates(final Collection<JobRunState> states) {
        return jobRunRepository.findAllByLatestStateIn(states).map(JobRunEntity::toJobRun).toList();
    }

    @Transactional(readOnly = true)
    public Collection<JobRun> findLatestRunsForEachJobIfInState(
            final Collection<JobRunState> states) {
        if (CollectionUtils.isEmpty(states)) {
            throw new IllegalArgumentException("States must not be empty");
        }
        return jobRunRepository
                .findLatestRunsForEachJobIfInState(states)
                .map(JobLatestRunEntity::toJobRun)
                .toList();
    }

    @Transactional(readOnly = true)
    public Collection<JobRunStateTransition> findJobRunStateTransitionsByJobRunId(
            final int jobRunId) {
        return jobRunStateTransitionRepository
                .findAllByJobRunId(jobRunId)
                .map(JobRunStateTransitionEntity::toJobRunStateTransition)
                .toList();
    }

    private Predicate buildJobRunsPredicate(
            final List<Integer> jobIds,
            final Collection<JobRunState> states,
            final Optional<Range<Instant>> dateTimeRange,
            final Root<JobRunEntity> root,
            final CriteriaBuilder criteriaBuilder) {

        final var predicates = new ArrayList<Predicate>();

        if (!jobIds.isEmpty()) {
            final var jobIdInConstraint = root.get("jobId").in(jobIds);
            predicates.add(jobIdInConstraint);
        }

        if (CollectionUtils.isNotEmpty(states)) {
            predicates.add(root.get("latestState").in(states));
        }

        dateTimeRange.ifPresent(range ->
                predicates.add(
                        criteriaBuilder.between(
                                root.get("startedAt"), range.getMinimum(), range.getMaximum())));

        return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    }

    @Transactional(readOnly = true)
    public Page<JobRun> findJobRuns(
            final Pageable pageable,
            final List<Integer> includeJobIds,
            final Collection<JobRunState> states,
            final Optional<Range<Instant>> fromToRange) {
        return jobRunRepository
                .findAll(
                        (root, query, criteriaBuilder) ->
                                buildJobRunsPredicate(
                                        includeJobIds, states, fromToRange, root, criteriaBuilder),
                        pageable)
                .map(JobRunEntity::toJobRun);
    }

    @Transactional
    public void insertNewJobs(final Collection<Class<? extends Job>> jobClass) {
        final var existingJobs =
                jobRepository.findAll().stream()
                        .map(JobEntity::canonicalClassName)
                        .collect(Collectors.toSet());

        final var toInsert =
                jobClass.stream()
                        .filter(cls -> !existingJobs.contains(cls.getCanonicalName()))
                        .toList();

        if (toInsert.isEmpty()) {
            return;
        }

        final var newEntities = toInsert.stream().map(JobEntity::newForClass).toList();
        jobRepository.saveAll(newEntities);
    }

    @Transactional(readOnly = true)
    public Collection<JobClassAndId> getAllJobsKnownByDatabase() {
        return jobRepository.findAll().stream().map(JobEntity::toRecord).toList();
    }

    @Transactional
    public void deleteJobsByIds(final Collection<Integer> jobIds) {
        jobRepository.deleteAllById(jobIds);
    }

    @Transactional
    public JobRun updateJobRunRunnerKeyAndArguments(
            final JobRun jobRun, final JobRunnerKey runnerKey, final JobRunArguments arguments) {
        return jobRunRepository
                .findById(jobRun.id())
                .map(entity -> {
                    entity.setRunnerKey(runnerKey);
                    entity.setJobRunArguments(arguments);
                    return jobRunRepository.save(entity);
                })
                .orElseThrow(throwJobRunNotFound(jobRun.id()))
                .toJobRun();
    }

    /** {@link #tryQueueNewRun}, returning the kept run in place of a discarded new one. */
    @Transactional
    public JobQueuedRun queueNewRun(
            final int jobId,
            final @Nullable Integer priority,
            final String versionTag,
            final JobRunArguments arguments,
            final Set<JobRunnerTaint> taints,
            final JobQueueBehavior queueBehavior) {
        return tryQueueNewRun(jobId, priority, versionTag, arguments, taints, queueBehavior).run();
    }

    /** Queues a new run, unless {@code queueBehavior} keeps a waiting or running one instead. */
    @Transactional
    public QueueNewRunResult tryQueueNewRun(
            final int jobId,
            final @Nullable Integer priority,
            final String versionTag,
            final JobRunArguments arguments,
            final Set<JobRunnerTaint> taints,
            final JobQueueBehavior queueBehavior) {
        final var blockingStates =
                switch (queueBehavior) {
                    case DISCARD_NEW -> Set.of(QUEUED, CLAIMED);
                    case DISCARD_IF_RUNNING -> Set.of(QUEUED, CLAIMED, RUNNING);
                    case REPLACE_RUN, ALLOW_MULTIPLE -> Set.<JobRunQueueState>of();
                };
        if (!blockingStates.isEmpty()) {
            // A RUNNING row is bound to a run by definition and does block; a *waiting* row that is
            // bound is a BaseStateJob continuation - work already under way, not a run waiting to
            // start - so it must not stand in for the newly requested one.
            final var blocking =
                    jobQueuedRunRepository
                            .findByJobIdAndQueueStateInOrderByCreatedAtAsc(jobId, blockingStates)
                            .stream()
                            .map(JobQueuedRunEntity::toJobQueuedRun)
                            .filter(row -> row.queueState() == RUNNING || row.jobRunId() == null)
                            .findFirst();
            if (blocking.isPresent()) {
                return new QueueNewRunResult(blocking.get(), false);
            }
        }
        if (queueBehavior == JobQueueBehavior.REPLACE_RUN) {
            // Only still-waiting rows of a fresh run. A CLAIMED row is already being submitted and
            // a row bound to a run is a BaseStateJob continuation - deleting either orphans or
            // strands its job run rather than stopping it.
            jobQueuedRunRepository.deleteByJobIdInAndJobRunIdIsNullAndQueueStateIn(
                    Set.of(jobId), Set.of(QUEUED));
        }
        final var queuedRunEntity =
                new JobQueuedRunEntity(jobId, priority, versionTag, arguments, taints);
        return new QueueNewRunResult(
                jobQueuedRunRepository.save(queuedRunEntity).toJobQueuedRun(), true);
    }

    /**
     * Idempotent: a run already {@code RUNNING} in the queue (e.g. dispatched to a pod) keeps its
     * row.
     */
    @Transactional
    public JobQueuedRun queueRunningRun(
            final int jobId,
            final String versionTag,
            final JobRunArguments arguments,
            final Set<JobRunnerTaint> taints,
            final int jobRunId,
            final String runner) {
        final var existing =
                jobQueuedRunRepository.findByJobRunIdAndQueueState(
                        jobRunId, JobRunQueueState.RUNNING);
        if (existing.isPresent()) {
            return existing.get().toJobQueuedRun();
        }
        final var queuedRunEntity =
                new JobQueuedRunEntity(jobId, versionTag, arguments, taints, jobRunId, runner);
        return jobQueuedRunRepository.save(queuedRunEntity).toJobQueuedRun();
    }

    /**
     * Moves a {@code BaseStateJob} run's own row back to {@code QUEUED} for {@code
     * processingRunnerKey}; inserts one only for a run started outside the queue.
     */
    @Transactional
    public JobQueuedRun queueReprocessingRun(
            final int jobId,
            final String versionTag,
            final JobRunArguments arguments,
            final Set<JobRunnerTaint> taints,
            final int jobRunId,
            final JobRunnerKey processingRunnerKey) {
        jobRunRepository
                .findById(jobRunId)
                .ifPresent(
                        entity -> {
                            entity.setRunnerKey(processingRunnerKey);
                            jobRunRepository.save(entity);
                        });
        if (jobQueuedRunRepository.requeueRunningRunForReprocessing(jobRunId, Instant.now()) > 0) {
            return jobQueuedRunRepository
                    .findByJobRunIdAndQueueState(jobRunId, QUEUED)
                    .orElseThrow()
                    .toJobQueuedRun();
        }
        final var queuedRunEntity =
                new JobQueuedRunEntity(jobId, versionTag, arguments, taints, jobRunId);
        return jobQueuedRunRepository.save(queuedRunEntity).toJobQueuedRun();
    }

    @Transactional
    public JobRun startJobRun(final int jobRunId, final Instant startedAt) {
        return jobRunRepository
                .findById(jobRunId)
                .map(entity -> {
                    entity.setStartedAt(startedAt);
                    return saveJobRunWithTransition(entity, JobRunState.BUSY, startedAt);
                })
                .orElseThrow(throwJobRunNotFound(jobRunId))
                .toJobRun();
    }

    @Transactional
    public void updateJobRunState(
            final int jobRunId, final JobRunState jobRunState, final Instant transitionedAt) {
        final var jobRunEntity =
                jobRunRepository.findById(jobRunId).orElseThrow(throwJobRunNotFound(jobRunId));

        final var latestState = jobRunEntity.latestState();
        if (jobRunEntity.finishedAt() != null && latestState != null && latestState.isRunning()) {
            // finishedAt should not be set for jobs in Running state
            jobRunEntity.setFinishedAt(null);
            jobRunRepository.save(jobRunEntity);
        }

        if (JobRunState.ACKNOWLEDGED == jobRunState
                && latestState != null
                && latestState.isAcknowledgeable()) {
            // Add state transition
            saveJobRunWithTransition(jobRunEntity, jobRunState, transitionedAt);
        }

        final var isFinishedState = !jobRunState.isRunning();
        if (jobRunEntity.finishedAt() != null && isFinishedState) {
            // Already finished, do not modify
            return;
        }

        if (isFinishedState) {
            // Set finished at
            jobRunEntity.setFinishedAt(transitionedAt);
        } else {
            // Make sure finished at is not set
            jobRunEntity.setFinishedAt(null);
        }

        // Add state transition
        saveJobRunWithTransition(jobRunEntity, jobRunState, transitionedAt);
    }

    @Transactional(readOnly = true)
    public Optional<JobRun> findJobRunById(final int jobRunId) {
        return jobRunRepository.findById(jobRunId).map(JobRunEntity::toJobRun);
    }

    @Transactional
    public void insertJobRunInput(final JobRunInputDeclaration inputDeclaration) {
        jobRunInputRepository.save(JobRunInputEntity.from(inputDeclaration));
    }

    @Transactional
    public void insertJobRunInput(final Collection<JobRunInputDeclaration> inputDeclarations) {
        final var now = Timestamp.from(Instant.now());
        jobJdbcTemplate.batchUpdate(
                """
                insert into t_job_run_input (fk_job_run_id, input_name, input_hash, created_at, updated_at)
                values (?, ?, ?, ?, ?)
                """,
                inputDeclarations,
                inputDeclarations.size(),
                (ps, e) -> {
                    ps.setInt(1, e.jobRunId());
                    ps.setString(2, e.path());
                    ps.setString(3, e.hash());
                    ps.setTimestamp(4, now);
                    ps.setTimestamp(5, now);
                });
        entityManager.flush();
    }

    @Transactional(readOnly = true)
    public Collection<JobRunInput> findJobRunInputs(final int runId) {
        return jobRunInputRepository.findAllByRunId(runId);
    }

    @Transactional(readOnly = true)
    public boolean hasJobRunInputs(final int runId) {
        return jobRunInputRepository.existsByRunId(runId);
    }

    @Transactional(readOnly = true)
    public Collection<Integer> jobRunIdsWithInputs(final Collection<Integer> runIds) {
        return jobRunInputRepository.findJobRunIdsWithInputs(runIds);
    }

    @Transactional
    public void insertLogEvents(final Collection<JobRunLoggingEvent> logEvents) {
        if (logEvents.isEmpty()) {
            return;
        }
        jobJdbcTemplate.batchUpdate(
                "insert into t_logging_event (fk_job_run_id, timestamp, formatted_message) values (?, ?, ?)",
                logEvents,
                logEvents.size(),
                (ps, e) -> {
                    ps.setInt(1, e.jobRunId());
                    ps.setTimestamp(2, Timestamp.from(e.timestamp()));
                    ps.setString(3, e.formattedMessage());
                });
        entityManager.flush();
    }

    @Transactional(readOnly = true)
    public Collection<LoggingEvent> findLogsForJobRun(final int jobRunId, final long skipLines) {
        return loggingEventRepository.findByJobRunIdOrderByTimestampAsc(jobRunId, (int) skipLines);
    }

    @Transactional(readOnly = true)
    public Collection<LoggingEvent> findAllLogsForJobRun(final int jobRunId) {
        return loggingEventRepository.findByJobRunIdOrderByTimestampAsc(jobRunId, 0);
    }

    @Transactional(readOnly = true)
    public Collection<JobProperty<Object>> getProperties(final int jobId) {
        return jobPropertyRepository
                .findAllByJobId(jobId)
                .map(JobPropertyEntity::toProperty)
                .filter(JobProperty::isValuePresent)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public void saveProperties(
            final int jobId, final Collection<JobProperty<? extends Object>> properties) {

        // Save all where value is set
        properties.stream()
                .filter(JobProperty::isValuePresent)
                .forEach(property -> saveProperty(jobId, property));

        // Delete all where value is unset
        jobPropertyRepository.deleteAll(
                properties.stream()
                        .filter(property -> !property.isValuePresent())
                        .map(property -> JobPropertyEntity.from(jobId, property))
                        .toList());
    }

    private <T> void saveProperty(final int jobId, final JobProperty<T> property) {
        final var updatedEntity =
                jobPropertyRepository
                        .findByJobIdAndKey(jobId, property.key())
                        .map(entity -> entity.setValue(property))
                        .orElseGet(() -> JobPropertyEntity.from(jobId, property));
        jobPropertyRepository.save(updatedEntity);
    }

    @Transactional(readOnly = true)
    public List<JobRun> findCleanableRuns(
            final int jobId,
            final int daysToKeep,
            final Optional<Integer> daysToKeepSkipped,
            final int chunkSize) {
        final var finishedBefore =
                jobRunRepository
                        .findFirstByJobIdAndLatestStateOrderByFinishedAtDesc(
                                jobId, JobRunState.DONE)
                        .map(JobRunEntity::toJobRun)
                        .map(jobRun ->
                                requireNonNull(jobRun.finishedAt())
                                        .minus(daysToKeep, ChronoUnit.DAYS))
                        .orElse(Instant.EPOCH);
        final var pageable = PageRequest.of(0, chunkSize);
        final var skippedBefore =
                daysToKeepSkipped.map(d -> Instant.now().minus(Duration.ofDays(d)));
        return skippedBefore
                .map(skipped ->
                        jobRunRepository
                                .findAllByJobWhereFinishedAtBeforeOrSkippedBefore(
                                        jobId, finishedBefore, skipped, pageable)
                                .map(JobRunEntity::toJobRun))
                .orElseGet(() ->
                        jobRunRepository
                                .findAllByJobIdAndFinishedAtBeforeOrderByFinishedAt(
                                        jobId, finishedBefore, pageable)
                                .map(JobRunEntity::toJobRun))
                .toList();
    }

    @Transactional
    public void deleteJobRunByIds(final List<Integer> jobRunIdList) {
        // batch delete might leave the first-level cache and the db out-of-sync - need to flush
        entityManager.flush();
        jobRunRepository.deleteAllByIdInBatch(jobRunIdList);
    }

    @Transactional
    public void deleteLogsForJobRunId(final int jobRunId) {
        loggingEventRepository.deleteAllByJobRunId(jobRunId);
    }

    @Transactional
    public void clearUnknownJobProperties() {
        final var jobPropertyNames =
                JobPropertyDefinitions.values().stream().map(JobPropertyDefinition::key).toList();
        jobPropertyRepository.deleteAllByKeyNotIn(jobPropertyNames);
    }

    /**
     * Attention, the DB triggers do not correspond to the effective triggers of the job, since
     * fixed and initial triggers are not taken into consideration.
     *
     * <p>Use {@link TriggerCoordinatorService#getTriggers(Job, Map)} to resolve the actual
     * triggers.
     */
    @Transactional(readOnly = true)
    public Map<Integer, List<Trigger>> getAllDbTriggersByJobId() {
        return jobTriggerRepository.findAll().stream()
                .map(JobTriggerEntity::toTrigger)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Trigger::jobId));
    }

    @Transactional
    public JobRun createJobRun(
            @Positive final int jobId,
            @NotBlank final String versionTag,
            final JobRunnerKey runnerKey,
            final JobRunArguments arguments) {
        final var scheduledAt = Instant.now();
        final var jobRunEntity =
                new JobRunEntity(jobId, versionTag, runnerKey, scheduledAt, arguments);
        return saveJobRunWithTransition(jobRunEntity, JobRunState.SCHEDULED, scheduledAt)
                .toJobRun();
    }

    @Transactional
    public void saveTrigger(final Trigger trigger) {
        jobTriggerRepository
                .findById(trigger.id())
                .ifPresentOrElse(
                        entity -> jobTriggerRepository.save(entity.update(trigger)),
                        () -> jobTriggerRepository.save(new JobTriggerEntity(trigger)));
    }

    @Transactional
    public void deleteTriggerForJob(final int jobId) {
        jobTriggerRepository.deleteAllByJobId(jobId);
    }

    @Transactional
    public void deleteTrigger(final Trigger trigger) {
        jobTriggerRepository.deleteById(trigger.id());
    }

    @Transactional
    public Collection<Trigger> insertTriggers(
            final int jobId, final Collection<TriggerDefinition> triggerDefinitions) {
        final var entities =
                triggerDefinitions.stream()
                        .map(triggerDefinition -> new JobTriggerEntity(jobId, triggerDefinition))
                        .toList();
        return jobTriggerRepository.saveAll(entities).stream()
                .map(JobTriggerEntity::toTrigger)
                .map(Objects::requireNonNull)
                .toList();
    }

    @Transactional
    public Trigger insertTrigger(final int jobId, final TriggerDefinition triggerDefinition) {
        final var entity = new JobTriggerEntity(jobId, triggerDefinition);
        return requireNonNull(jobTriggerRepository.save(entity).toTrigger());
    }

    @Transactional
    public void insertNewDatabaseSubmission(
            final int jobId,
            final JobRunDatabaseSubmissionArguments jobRunDatabaseSubmissionArguments) {
        final var submittedAt = Instant.now();
        final var entity =
                new JobRunDatabaseSubmissionEntity(
                        jobId, submittedAt, jobRunDatabaseSubmissionArguments);
        jobRunDatabaseSubmissionRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public Collection<JobRunDatabaseSubmission> findNewDatabaseSubmissions() {
        return jobRunDatabaseSubmissionRepository
                .findAllByExecutedAtIsNull()
                .map(JobRunDatabaseSubmissionEntity::toJobRunDatabaseSubmission)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public void markDatabaseSubmissionAsExecuted(final int databaseSubmissionId) {
        final var entity =
                jobRunDatabaseSubmissionRepository
                        .findById(databaseSubmissionId)
                        .orElseThrow(
                                ResourceNotFoundException.supplierWithMessage(
                                        "No JobRunDatabaseSubmissionEntity with id {0} found",
                                        databaseSubmissionId));
        entity.setExecutedAt(Instant.now());
        jobRunDatabaseSubmissionRepository.save(entity);
    }

    @Transactional
    public void deleteJobRunDatabaseSubmissionsByJobId(final int jobId) {
        jobRunDatabaseSubmissionRepository.deleteByJobId(jobId);
    }

    @Transactional
    public void saveK8sJob(final int jobRunId, final String k8sJobName) {
        k8sJobRepository.save(new K8sJobEntity(jobRunId, k8sJobName));
    }

    @Transactional(readOnly = true)
    public Optional<String> findK8sJobName(final int jobRunId) {
        return k8sJobRepository.findFirstByJobRunId(jobRunId).map(K8sJobEntity::k8sJobName);
    }

    @Transactional(readOnly = true)
    public List<JobRunStateTransition> findJobRunStateTransitionsSince(
            final Instant transitionsSince) {
        return jobRunStateTransitionRepository
                .findAllByTransitionedAtAfterOrderByTransitionedAt(transitionsSince)
                .map(JobRunStateTransitionEntity::toJobRunStateTransition)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Instant> findMostRecentLoggingEventTimestamp(final int jobRunId) {
        return loggingEventRepository
                .findFirstByJobRunIdOrderByTimestampDesc(jobRunId)
                .map(LoggingEventEntity::getTimestamp);
    }

    @Transactional(readOnly = true)
    public Collection<JobClassAndId> getEnabledJobs() {
        return jobRepository.findAll().stream()
                .filter(JobEntity::isEnabled)
                .map(JobEntity::toRecord)
                .toList();
    }

    @Transactional
    public void setEnabled(final int jobId, final boolean isEnabled) {
        jobRepository.setEnabled(jobId, isEnabled);
    }

    private static Supplier<ResourceNotFoundException> throwJobRunNotFound(final int jobRunId) {
        return ResourceNotFoundException.supplierWithMessage(
                "No JobRunEntity with id {0} found", jobRunId);
    }

    private JobRunEntity saveJobRunWithTransition(
            final JobRunEntity jobRunEntity,
            final JobRunState state,
            final Instant transitionedAt) {

        jobRunEntity.setLatestState(state);
        final var savedJobRun = jobRunRepository.save(jobRunEntity);
        final var transition = new JobRunStateTransitionEntity(savedJobRun, state, transitionedAt);
        jobRunStateTransitionRepository.save(transition);

        // Check what the actual latest transition is
        final var latestTransitionEntity =
                jobRunStateTransitionRepository.findFirstByJobRunIdOrderByTransitionedAtDesc(
                        requireNonNull(savedJobRun.id()));
        if (latestTransitionEntity != null) {
            final var latestTransitionState = latestTransitionEntity.state();
            if (latestTransitionState != state) {
                // There is a newer state transition in the database, update latest state of run
                // accordingly
                savedJobRun.setLatestState(latestTransitionState);
                jobRunRepository.save(savedJobRun);
            }
        }
        return savedJobRun;
    }

    /**
     * Ordered, paged view of queued runs - see {@link
     * JobQueuedRunRepository#findByQueueStateOrderedByPriority}.
     */
    @Transactional(readOnly = true)
    public Slice<JobQueuedRun> findQueuedRunsPage(final Pageable pageable) {
        return jobQueuedRunRepository
                .findByQueueStateOrderedByPriority(QUEUED, pageable)
                .map(JobQueuedRunEntity::toJobQueuedRun);
    }

    @Transactional
    public boolean claimQueuedRun(
            final JobQueuedRun jobQueuedRun, final String runnerInstanceName) {
        final var updated =
                jobQueuedRunRepository.setState(
                        jobQueuedRun.jobQueuedRunId(),
                        jobQueuedRun.queueState(),
                        CLAIMED,
                        runnerInstanceName,
                        Instant.now());
        return updated == 1;
    }

    /**
     * Links a claimed row to the run created for it and marks it {@code RUNNING}. Returns whether
     * the row was still claimed - not fatal when {@code false}, since the run is already under way.
     */
    @Transactional
    public boolean setRunId(final JobQueuedRun jobQueuedRun, final int runId) {
        return jobQueuedRunRepository.linkRunAndMarkRunning(
                        jobQueuedRun.jobQueuedRunId(), runId, Instant.now())
                == 1;
    }

    /**
     * Marks a still-{@code QUEUED} row as wanted by an instance waiting to run it, so {@code
     * QUEUE_TIMEOUT} doesn't cancel it meanwhile. Returns whether the row was still queued.
     */
    @Transactional
    public boolean touchWaitingQueuedRun(final int jobQueuedRunId) {
        return jobQueuedRunRepository.touchQueuedRun(jobQueuedRunId, Instant.now()) == 1;
    }

    @Transactional(readOnly = true)
    public List<JobQueuedRun> findQueuedRunsByState(final JobRunQueueState state) {
        return jobQueuedRunRepository.findAllByQueueState(state).stream()
                .map(JobQueuedRunEntity::toJobQueuedRun)
                .toList();
    }

    /**
     * Reclaims rows stuck in {@code CLAIMED} whose last update (the claim itself) is older than
     * {@code staleBefore} - the claiming instance died before ever submitting.
     */
    @Transactional
    public int resetStaleClaims(final Map<Integer, Instant> staleBefore) {
        final var now = Instant.now();
        return groupJobsByCutoff(staleBefore).entrySet().stream()
                .mapToInt(
                        entry ->
                                jobQueuedRunRepository.resetStaleClaimsForJobs(
                                        entry.getValue(), CLAIMED, QUEUED, entry.getKey(), now))
                .sum();
    }

    /**
     * Inverts a per-job cutoff map into one entry per <em>distinct</em> cutoff. Jobs almost always
     * share a timeout, so this turns one statement per job into one per configured value.
     */
    private static Map<Instant, List<Integer>> groupJobsByCutoff(
            final Map<Integer, Instant> byJob) {
        return byJob.entrySet().stream()
                .collect(
                        Collectors.groupingBy(
                                Map.Entry::getValue,
                                Collectors.mapping(Map.Entry::getKey, Collectors.toList())));
    }

    /**
     * Marks {@code QUEUED} rows whose last activity is before {@code staleBefore} as {@link
     * JobRunQueueState#CANCELLED} - unclaimable (see {@code QUEUE_TIMEOUT}).
     */
    @Transactional
    public int expireStaleQueuedRuns(final Map<Integer, Instant> staleBefore) {
        final var now = Instant.now();
        return groupJobsByCutoff(staleBefore).entrySet().stream()
                .mapToInt(
                        entry ->
                                jobQueuedRunRepository.expireStaleQueuedRunsForJobs(
                                        entry.getValue(), entry.getKey(), now))
                .sum();
    }

    /**
     * Deletes {@code CANCELLED} rows cancelled before {@code retainAfter} (see {@code
     * CANCELLED_QUEUE_RETENTION}).
     */
    @Transactional
    public int deleteOldCancelledQueuedRuns(final Map<Integer, Instant> retainAfter) {
        return groupJobsByCutoff(retainAfter).entrySet().stream()
                .mapToInt(
                        entry ->
                                jobQueuedRunRepository.deleteOldCancelledQueuedRunsForJobs(
                                        entry.getValue(), entry.getKey()))
                .sum();
    }

    /** Deletes a single {@link JobRunQueueState#CANCELLED} row; returns whether there was one. */
    @Transactional
    public boolean acknowledgeQueuedRun(final int jobQueuedRunId) {
        return jobQueuedRunRepository.deleteByJobQueuedRunIdAndQueueState(jobQueuedRunId, CANCELLED)
                > 0;
    }

    /** Acknowledges (deletes) every {@link JobRunQueueState#CANCELLED} row. Returns how many. */
    @Transactional
    public long acknowledgeAllCancelledQueuedRuns() {
        return jobQueuedRunRepository.deleteByQueueState(CANCELLED);
    }

    /**
     * Unconditional delete for internal cleanup once a linked job run has actually finished -
     * unlike {@link #deleteQueuedRunIfQueued}, this is not meant for admin use.
     */
    @Transactional
    public void deleteQueuedRunById(final int jobQueuedRunId) {
        jobQueuedRunRepository.deleteById(jobQueuedRunId);
    }

    /**
     * Unconditional reset to {@code QUEUED}, restoring {@code jobRunId} as it was before the claim.
     */
    @Transactional
    public void resetQueuedRunById(final int jobQueuedRunId, final @Nullable Integer jobRunId) {
        jobQueuedRunRepository.forceReset(jobQueuedRunId, QUEUED, jobRunId, Instant.now());
    }

    /** Cancels a still-{@code QUEUED} row nothing can execute; returns whether it did. */
    @Transactional
    public boolean cancelUnclaimableQueuedRun(final int jobQueuedRunId) {
        return jobQueuedRunRepository.cancelQueuedRunById(jobQueuedRunId, Instant.now()) == 1;
    }

    /**
     * Deletes {@code CANCELLED} rows belonging to no job in {@code knownJobIds} once older than
     * {@code retainAfter} - the per-job sweep can never reach those.
     */
    @Transactional
    public int deleteOldCancelledQueuedRunsForUnknownJobs(
            final Collection<Integer> knownJobIds, final Instant retainAfter) {
        // Job ids are positive; a sentinel keeps NOT IN valid when no jobs are known.
        final var notIn = knownJobIds.isEmpty() ? Set.of(-1) : knownJobIds;
        return jobQueuedRunRepository.deleteOldCancelledQueuedRunsForUnknownJobs(
                notIn, retainAfter);
    }

    /**
     * Only updates a {@link JobRunQueueState#QUEUED} row's priority - once claimed (or running), a
     * runner already has it. Returns whether a row was updated.
     */
    @Transactional
    public boolean updateQueuedRunPriority(
            final int jobQueuedRunId, final @Nullable Integer priority) {
        return jobQueuedRunRepository.updatePriority(
                        jobQueuedRunId, priority, JobRunQueueState.QUEUED)
                == 1;
    }

    private Predicate buildQueuedRunsPredicate(
            final List<Integer> jobIds,
            final Collection<JobRunQueueState> states,
            final Optional<Range<Instant>> dateTimeRange,
            final Root<JobQueuedRunEntity> root,
            final CriteriaBuilder criteriaBuilder) {

        final var predicates = new ArrayList<Predicate>();

        if (!jobIds.isEmpty()) {
            predicates.add(root.get("jobId").in(jobIds));
        }

        if (CollectionUtils.isNotEmpty(states)) {
            predicates.add(root.get("queueState").in(states));
        }

        dateTimeRange.ifPresent(range ->
                predicates.add(
                        criteriaBuilder.between(
                                root.get("createdAt"), range.getMinimum(), range.getMaximum())));

        return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    }

    @Transactional(readOnly = true)
    public Page<JobQueuedRun> findQueuedRuns(
            final Pageable pageable,
            final List<Integer> includeJobIds,
            final Collection<JobRunQueueState> states,
            final Optional<Range<Instant>> fromToRange) {
        return jobQueuedRunRepository
                .findAll(
                        (root, query, criteriaBuilder) ->
                                buildQueuedRunsPredicate(
                                        includeJobIds, states, fromToRange, root, criteriaBuilder),
                        pageable)
                .map(JobQueuedRunEntity::toJobQueuedRun);
    }

    @Transactional(readOnly = true)
    public Optional<JobQueuedRun> findOldestQueuedRun() {
        return jobQueuedRunRepository
                .findFirstByOrderByCreatedAtAsc()
                .map(JobQueuedRunEntity::toJobQueuedRun);
    }

    @Transactional(readOnly = true)
    public Optional<JobQueuedRun> findNewestQueuedRun() {
        return jobQueuedRunRepository
                .findFirstByOrderByCreatedAtDesc()
                .map(JobQueuedRunEntity::toJobQueuedRun);
    }

    @Transactional
    public boolean deleteQueuedRunIfQueued(final int jobQueuedRunId) {
        return jobQueuedRunRepository.deleteByJobQueuedRunIdAndJobRunIdIsNullAndQueueState(
                        jobQueuedRunId, QUEUED)
                > 0;
    }

    /**
     * Never removes a {@link JobRunQueueState#RUNNING} row - callers must exclude it from {@code
     * states} themselves; this method does not filter it out.
     */
    @Transactional
    public long deleteQueuedRuns(
            final Collection<Integer> jobIds, final Collection<JobRunQueueState> states) {
        if (jobIds.isEmpty() || states.isEmpty()) {
            return 0;
        }
        return jobQueuedRunRepository.deleteByJobIdInAndJobRunIdIsNullAndQueueStateIn(
                jobIds, states);
    }

    @Transactional(readOnly = true)
    public List<JobQueuedRun> findQueuedRunsByIds(final Collection<Integer> queueRunIds) {
        return jobQueuedRunRepository.findAllById(queueRunIds).stream()
                .map(JobQueuedRunEntity::toJobQueuedRun)
                .toList();
    }

    /** The given runs, by id - for reconciling many queue rows without a query each. */
    @Transactional(readOnly = true)
    public Map<Integer, JobRun> findJobRunsByIds(final Collection<Integer> jobRunIds) {
        if (jobRunIds.isEmpty()) {
            return Map.of();
        }
        return jobRunRepository.findAllById(jobRunIds).stream()
                .map(JobRunEntity::toJobRun)
                .collect(MapCollectors.toMap(JobRun::id));
    }

    /** Refreshes the instance's row, re-inserting it if purged; never touches the lease. */
    @Transactional
    public JobDaemon checkInDaemon(
            final String daemonName,
            final String version,
            final Set<String> taints,
            final boolean primaryCandidate,
            final @Nullable Integer triggerCount,
            final Instant startedAt) {
        final var now = Instant.now();
        final var entity =
                jobDaemonRepository
                        .findByDaemonName(daemonName)
                        .map(
                                existing ->
                                        existing.checkIn(
                                                version,
                                                taints,
                                                primaryCandidate,
                                                triggerCount,
                                                startedAt,
                                                now))
                        .orElseGet(
                                () ->
                                        new JobDaemonEntity(
                                                daemonName,
                                                version,
                                                taints,
                                                primaryCandidate,
                                                triggerCount,
                                                startedAt,
                                                now));
        return jobDaemonRepository.save(entity).toJobDaemon();
    }

    @Transactional(readOnly = true)
    public List<JobDaemon> findLiveDaemons(final Instant staleBefore) {
        return jobDaemonRepository.findByUpdatedAtAfter(staleBefore).stream()
                .map(JobDaemonEntity::toJobDaemon)
                .toList();
    }

    @Transactional
    public int deleteStaleInstances(final Instant staleBefore) {
        return jobDaemonRepository.deleteStale(staleBefore);
    }

    /**
     * Takes the lease unless a newer live instance holds it; returns whether it now does. A
     * concurrent take throws {@code DataIntegrityViolationException}.
     */
    @Transactional
    public boolean takePrimaryLease(
            final String daemonName, final Instant startedAt, final Instant staleBefore) {
        jobDaemonRepository.releaseOutrankedPrimaryLease(daemonName, startedAt, staleBefore);
        final var holder = jobDaemonRepository.findFirstByPrimarySlotIsNotNull();
        if (holder.isPresent()) {
            return holder.get().toJobDaemon().daemonName().equals(daemonName);
        }
        return jobDaemonRepository.acquirePrimaryLease(daemonName, Instant.now(), staleBefore) == 1;
    }

    /** Share-locks the row until the transaction ends, so the lease can't move meanwhile. */
    @Transactional
    public boolean holdsPrimaryLease(final String daemonName) {
        return jobDaemonRepository.findByDaemonNameAndPrimarySlotIsNotNull(daemonName).isPresent();
    }

    /**
     * {@link #queueNewRun} if {@code daemonName} holds the lease, checked in the same transaction.
     */
    @Transactional
    public Optional<JobQueuedRun> queueNewRunIfPrimary(
            final String daemonName,
            final int jobId,
            final @Nullable Integer priority,
            final String versionTag,
            final JobRunArguments arguments,
            final Set<JobRunnerTaint> taints,
            final JobQueueBehavior queueBehavior) {
        if (!holdsPrimaryLease(daemonName)) {
            return Optional.empty();
        }
        return Optional.of(
                queueNewRun(jobId, priority, versionTag, arguments, taints, queueBehavior));
    }

    /** Deletes the instance, handing a held lease to {@code handOverTo} in the same transaction. */
    @Transactional
    public void removeDaemon(
            final String daemonName, final @Nullable String handOverTo, final Instant staleBefore) {
        if (jobDaemonRepository.releasePrimaryLease(daemonName) == 1 && handOverTo != null) {
            jobDaemonRepository.acquirePrimaryLease(handOverTo, Instant.now(), staleBefore);
        }
        jobDaemonRepository.deleteByDaemonName(daemonName);
    }
}
