package ch.ubique.backend.job.data.service;

import static ch.ubique.backend.job.model.queue.JobRunQueueState.CLAIMED;
import static ch.ubique.backend.job.model.queue.JobRunQueueState.QUEUED;

import static java.util.Objects.requireNonNull;

import ch.ubique.backend.core.exception.ResourceNotFoundException;
import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.data.repository.*;
import ch.ubique.backend.job.logging.JobRunLoggingEvent;
import ch.ubique.backend.job.logging.LoggingEvent;
import ch.ubique.backend.job.model.*;
import ch.ubique.backend.job.model.entity.*;
import ch.ubique.backend.job.model.input.JobRunInputDeclaration;
import ch.ubique.backend.job.model.properties.*;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.queue.JobRunQueueState;
import ch.ubique.backend.job.model.runner.conditions.JobRunnerTaint;
import ch.ubique.backend.job.model.triggers.*;
import ch.ubique.backend.job.model.triggers.TriggerDefinition;
import ch.ubique.backend.job.service.triggers.TriggerCoordinatorService;

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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

@Service
public class JobDataService {

    private final JobRepository jobRepository;
    private final JobTriggerRepository jobTriggerRepository;
    private final JobPropertyRepository jobPropertyRepository;
    private final JobRunRepository jobRunRepository;
    private final JobRunStateTransitionRepository jobRunStateTransitionRepository;
    private final JobQueuedRunRepository jobQueuedRunRepository;
    private final JobRunInputRepository jobRunInputRepository;
    private final JobRunDatabaseSubmissionRepository jobRunDatabaseSubmissionRepository;
    private final K8sJobRepository k8sJobRepository;
    private final LoggingEventRepository loggingEventRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
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
            final K8sJobRepository k8sJobRepository,
            final LoggingEventRepository loggingEventRepository,
            final NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            final JdbcTemplate jobJdbcTemplate) {
        this.jobRepository = jobRepository;
        this.jobTriggerRepository = jobTriggerRepository;
        this.jobPropertyRepository = jobPropertyRepository;
        this.jobRunRepository = jobRunRepository;
        this.jobRunStateTransitionRepository = jobRunStateTransitionRepository;
        this.jobRunInputRepository = jobRunInputRepository;
        this.jobRunDatabaseSubmissionRepository = jobRunDatabaseSubmissionRepository;
        this.jobQueuedRunRepository = jobQueuedRunRepository;
        this.k8sJobRepository = k8sJobRepository;
        this.loggingEventRepository = loggingEventRepository;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
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

        dateTimeRange.ifPresent(
                range ->
                        predicates.add(
                                criteriaBuilder.between(
                                        root.get("startedAt"),
                                        range.getMinimum(),
                                        range.getMaximum())));

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
    public void insertNewJobs(
            final Collection<Class<? extends Job>> jobClass,
            final ToIntFunction<Class<? extends Job>> priorityResolver) {
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

        final var newEntities =
                toInsert.stream()
                        .map(cls -> JobEntity.newForClass(cls, priorityResolver.applyAsInt(cls)))
                        .toList();
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
                .map(
                        entity -> {
                            entity.setRunnerKey(runnerKey);
                            entity.setJobRunArguments(arguments);
                            return jobRunRepository.save(entity);
                        })
                .orElseThrow(throwJobRunNotFound(jobRun.id()))
                .toJobRun();
    }

    @Transactional
    public JobQueuedRun queueNewRun(
            final int jobId,
            final @Nullable Integer priority,
            final String versionTag,
            final JobRunArguments arguments,
            final Set<JobRunnerTaint> taints,
            final boolean allowMultipleQueuedRuns) {
        final var queuedRunEntity =
                new JobQueuedRunEntity(jobId, priority, versionTag, arguments, taints);
        if (!allowMultipleQueuedRuns) {
            // TODO: Default behavior is to keep the latest job run only, is there another
            //  reasonable behavior we need to support?
            final var existing = jobQueuedRunRepository.findAllByJobId(jobId);
            if (!existing.isEmpty()) {
                jobQueuedRunRepository.deleteAll(existing);
            }
        }
        return jobQueuedRunRepository.save(queuedRunEntity).toJobQueuedRun();
    }

    @Transactional
    public JobRun startJobRun(final int jobRunId, final Instant startedAt) {
        return jobRunRepository
                .findById(jobRunId)
                .map(
                        entity -> {
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
                        .map(
                                jobRun ->
                                        Objects.requireNonNull(jobRun.finishedAt())
                                                .minus(daysToKeep, ChronoUnit.DAYS))
                        .orElse(Instant.EPOCH);
        final var pageable = PageRequest.of(0, chunkSize);
        final var skippedBefore =
                daysToKeepSkipped.map(d -> Instant.now().minus(Duration.ofDays(d)));
        return skippedBefore
                .map(
                        skipped ->
                                jobRunRepository
                                        .findAllByJobWhereFinishedAtBeforeOrSkippedBefore(
                                                jobId, finishedBefore, skipped, pageable)
                                        .map(JobRunEntity::toJobRun))
                .orElseGet(
                        () ->
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

    @Transactional(readOnly = true)
    public Map<Integer, Integer> getPriorities() {
        return jobRepository.findAll().stream()
                .collect(Collectors.toMap(entity -> entity.toRecord().id(), JobEntity::priority));
    }

    @Transactional
    public void updatePriority(final int jobId, final int priority) {
        jobRepository.setPriority(jobId, priority);
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

    @Transactional(readOnly = true)
    public List<JobQueuedRun> findFirstQueuedByNoOtherTaints(
            final int number, final Set<String> instanceTaints) {
        return jobQueuedRunRepository
                .findByTaints(instanceTaints, Pageable.ofSize(number))
                .map(JobQueuedRunEntity::toJobQueuedRun)
                .toList();
    }

    @Transactional
    public boolean claimQueuedRun(
            final JobQueuedRun jobQueuedRun, final String runnerInstanceName) {
        final var updated =
                jobQueuedRunRepository.setState(
                        jobQueuedRun.jobQueuedRunId(),
                        jobQueuedRun.queueState(),
                        JobRunQueueState.CLAIMED,
                        runnerInstanceName,
                        Instant.now());
        return updated == 1;
    }

    public void setRunId(final JobQueuedRun jobQueuedRun, final int runId) {
        final var entity =
                jobQueuedRunRepository.findById(jobQueuedRun.jobQueuedRunId()).orElseThrow();
        jobQueuedRunRepository.save(entity.setRunId(runId));
    }

    @Transactional(readOnly = true)
    public List<JobQueuedRun> findQueuedRunsByState(final JobRunQueueState state) {
        return jobQueuedRunRepository.findAllByQueueState(state).stream()
                .map(JobQueuedRunEntity::toJobQueuedRun)
                .toList();
    }

    /**
     * Reclaims rows stuck in {@code CLAIMED} whose heartbeat is older than {@code staleBefore}
     */
    @Transactional
    public int resetStaleClaims(final Map<Integer, Instant> staleBefore) {
        return Arrays.stream(
                        namedParameterJdbcTemplate.batchUpdate(
                                """
                                        UPDATE t_job_run_queue
                                        SET queue_state = :resetState, runner = null, heartbeat_at = null, updated_at = :now
                                        WHERE fk_job_id = :fkJobId AND queue_state = :staleState
                                          AND (heartbeat_at IS NULL OR heartbeat_at < :staleBefore)
                                        """,
                                staleBefore.entrySet().stream()
                                        .map(
                                                entry -> {
                                                    final var jobId = entry.getKey();
                                                    final var staleBeforeVal = entry.getValue();
                                                    return new MapSqlParameterSource()
                                                            .addValue("fkJobId", jobId)
                                                            .addValue("resetState", QUEUED)
                                                            .addValue("staleState", CLAIMED)
                                                            .addValue("staleBefore", staleBeforeVal)
                                                            .addValue("now", Instant.now());
                                                })
                                        .toArray(MapSqlParameterSource[]::new)))
                .sum();
    }

    @Transactional
    public void touchQueuedRunHeartbeat(final int jobQueuedRunId) {
        jobQueuedRunRepository.touchHeartbeat(jobQueuedRunId, Instant.now());
    }

    /**
     * Unconditional delete for internal cleanup once a linked job run has actually finished -
     * unlike {@link #deleteQueuedRunIfNotRunning}, this is not meant for admin use.
     */
    @Transactional
    public void deleteQueuedRunById(final int jobQueuedRunId) {
        jobQueuedRunRepository.deleteById(jobQueuedRunId);
    }

    /** Unconditionally resets a single row back to {@link JobRunQueueState#QUEUED}. */
    @Transactional
    public void resetQueuedRunById(final int jobQueuedRunId) {
        jobQueuedRunRepository.forceReset(jobQueuedRunId, QUEUED, Instant.now());
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

        dateTimeRange.ifPresent(
                range ->
                        predicates.add(
                                criteriaBuilder.between(
                                        root.get("createdAt"),
                                        range.getMinimum(),
                                        range.getMaximum())));

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

    /** Never removes a {@link JobRunQueueState#RUNNING} row. */
    @Transactional
    public boolean deleteQueuedRunIfNotRunning(final int jobQueuedRunId) {
        return jobQueuedRunRepository.deleteByJobQueuedRunIdAndQueueStateNot(
                        jobQueuedRunId, JobRunQueueState.RUNNING)
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
        return jobQueuedRunRepository.deleteByJobIdInAndQueueStateIn(jobIds, states);
    }
}
