package ch.ubique.backend.job.data.service;

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
import ch.ubique.backend.job.model.triggers.*;
import ch.ubique.backend.job.model.triggers.TriggerDefinition;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Range;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final JobRunLatestStateViewRepository jobRunLatestStateViewRepository;
    private final JobRunStateTransitionRepository jobRunStateTransitionRepository;
    private final JobRunInputRepository jobRunInputRepository;
    private final JobRunDatabaseSubmissionRepository jobRunDatabaseSubmissionRepository;
    private final K8sJobRepository k8sJobRepository;
    private final LoggingEventRepository loggingEventRepository;
    private final JdbcTemplate jdbcTemplate;

    @PersistenceContext private EntityManager entityManager;

    public JobDataService(
            final JobRepository jobRepository,
            final JobTriggerRepository jobTriggerRepository,
            final JobPropertyRepository jobPropertyRepository,
            final JobRunRepository jobRunRepository,
            final JobRunLatestStateViewRepository jobRunLatestStateViewRepository,
            final JobRunStateTransitionRepository jobRunStateTransitionRepository,
            final JobRunInputRepository jobRunInputRepository,
            final JobRunDatabaseSubmissionRepository jobRunDatabaseSubmissionRepository,
            final K8sJobRepository k8sJobRepository,
            final LoggingEventRepository loggingEventRepository,
            final JdbcTemplate jdbcTemplate) {
        this.jobRepository = jobRepository;
        this.jobTriggerRepository = jobTriggerRepository;
        this.jobPropertyRepository = jobPropertyRepository;
        this.jobRunRepository = jobRunRepository;
        this.jobRunLatestStateViewRepository = jobRunLatestStateViewRepository;
        this.jobRunStateTransitionRepository = jobRunStateTransitionRepository;
        this.jobRunInputRepository = jobRunInputRepository;
        this.jobRunDatabaseSubmissionRepository = jobRunDatabaseSubmissionRepository;
        this.k8sJobRepository = k8sJobRepository;
        this.loggingEventRepository = loggingEventRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Optional<JobRun> findLatestJobRun(final int jobId) {
        return jobRunRepository
                .findFirstByJobIdOrderByStartedAtDesc(jobId)
                .map(JobRunEntity::toJobRun);
    }

    @Transactional(readOnly = true)
    public Optional<JobRun> findLatestJobRunByState(final int jobId, final JobRunState state) {
        return jobRunLatestStateViewRepository
                .findFirstByJobIdAndLatestStateOrderByStartedAtDesc(jobId, state)
                .map(JobRunLatestStateViewEntity::toJobRun);
    }

    @Transactional(readOnly = true)
    public Optional<JobRun> findAnyJobRunInStatesSince(
            final int jobId, final Collection<JobRunState> states, final Instant after) {
        if (states.isEmpty()) {
            return Optional.empty();
        }
        return jobRunLatestStateViewRepository
                .findAnyJobRunInStateSince(jobId, states, after)
                .map(JobRunLatestStateViewEntity::toJobRun);
    }

    @Transactional(readOnly = true)
    public Optional<JobRun> findLatestJobRunByStates(
            final int jobId, final Collection<JobRunState> states) {
        return jobRunLatestStateViewRepository
                .findFirstByJobIdAndLatestStateInOrderByStartedAtDesc(jobId, states)
                .map(JobRunLatestStateViewEntity::toJobRun);
    }

    @Transactional(readOnly = true)
    public Collection<JobRun> findJobRunByStates(final Collection<JobRunState> states) {
        return jobRunLatestStateViewRepository
                .findAllByLatestStateIn(states)
                .map(JobRunLatestStateViewEntity::toJobRun)
                .toList();
    }

    @Transactional(readOnly = true)
    public Collection<JobRun> findLatestRunsForEachJobIfInState(
            final Collection<JobRunState> states) {
        if (CollectionUtils.isEmpty(states)) {
            throw new IllegalArgumentException("States must not be empty");
        }
        return jobRunLatestStateViewRepository
                .findLatestRunsForEachJobIfInState(states)
                .map(JobRunLatestStateViewEntity::toJobRun)
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
            final Root<JobRunLatestStateViewEntity> root,
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
            @NonNull final List<Integer> includeJobIds,
            final Collection<JobRunState> states,
            final Optional<Range<Instant>> fromToRange) {
        return jobRunLatestStateViewRepository
                .findAll(
                        (root, query, criteriaBuilder) ->
                                buildJobRunsPredicate(
                                        includeJobIds, states, fromToRange, root, criteriaBuilder),
                        pageable)
                .map(JobRunLatestStateViewEntity::toJobRun);
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

        final var newEntities = toInsert.stream().map(JobEntity::forClass).toList();
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
            @NonNull final JobRun jobRun,
            @NonNull final JobRunnerKey runnerKey,
            final JobRunArguments arguments) {
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
    public JobRun startJobRun(
            final int jobRunId,
            @NonNull final JobRunState jobRunState,
            @NonNull final Instant startedAt) {
        return jobRunRepository
                .findById(jobRunId)
                .map(
                        entity -> {
                            entity.setStartedAt(startedAt);
                            entity.addJobRunStateTransition(
                                    new JobRunStateTransitionEmbeddable(jobRunState, startedAt));
                            return jobRunRepository.save(entity);
                        })
                .orElseThrow(throwJobRunNotFound(jobRunId))
                .toJobRun();
    }

    @Transactional
    public void updateJobRunState(
            final int jobRunId,
            final @NonNull JobRunState jobRunState,
            final @NonNull Instant transitionedAt) {
        final var jobRunEntity =
                jobRunRepository.findById(jobRunId).orElseThrow(throwJobRunNotFound(jobRunId));

        if (jobRunEntity.finishedAt() != null && jobRunEntity.latestState().isRunning()) {
            // finishedAt should not be set for jobs in Running state
            jobRunEntity.setFinishedAt(null);
            jobRunRepository.save(jobRunEntity);
        }

        if (JobRunState.ACKNOWLEDGED == jobRunState
                && jobRunEntity.latestState().isAcknowledgeable()) {
            // Add state transition
            jobRunEntity.addJobRunStateTransition(
                    new JobRunStateTransitionEmbeddable(jobRunState, transitionedAt));
            jobRunRepository.save(jobRunEntity);
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
        jobRunEntity.addJobRunStateTransition(
                new JobRunStateTransitionEmbeddable(jobRunState, transitionedAt));

        // Save
        jobRunRepository.save(jobRunEntity);
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
        jobRunInputRepository.saveAll(
                inputDeclarations.stream().map(JobRunInputEntity::from).toList());
    }

    @Transactional(readOnly = true)
    public Collection<JobRunInput> findJobRunInputs(final int runId) {
        return jobRunInputRepository.findAllByRunId(runId);
    }

    @Transactional(readOnly = true)
    public boolean hasJobRunInputs(final int runId) {
        return jobRunInputRepository.existsByRunId(runId);
    }

    @Transactional
    public void insertLogEvents(final Collection<JobRunLoggingEvent> logEvents) {
        if (logEvents.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                "insert into t_logging_event (fk_job_run_id, timestamp, formatted_message) values (?, ?, ?)",
                logEvents,
                logEvents.size(),
                (ps, e) -> {
                    ps.setInt(1, e.jobRunId());
                    ps.setObject(2, java.sql.Timestamp.from(e.timestamp()));
                    ps.setString(3, e.formattedMessage());
                });
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
    public void saveProperties(final int jobId, final Collection<JobProperty<?>> properties) {

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

    private void saveProperty(final int jobId, final JobProperty<?> property) {
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
                jobRunLatestStateViewRepository
                        .findFirstByJobIdAndLatestStateAndFinishedAtIsNotNullOrderByFinishedAtDesc(
                                jobId, JobRunState.DONE)
                        .map(JobRunLatestStateViewEntity::toJobRun)
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
                                        .map(JobRunLatestStateViewEntity::toJobRun))
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

    @Transactional(readOnly = true)
    public Collection<Trigger> getTriggers(final int jobId) {
        return jobTriggerRepository
                .findAllByJobId(jobId)
                .map(JobTriggerEntity::toTrigger)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public JobRun createJobRun(
            @Positive final int jobId,
            @NotBlank final String versionTag,
            @NonNull final JobRunnerKey runnerKey,
            final JobRunArguments arguments) {
        final var scheduledAt = Instant.now();
        final var jobRunEntity =
                new JobRunEntity(jobId, versionTag, runnerKey, scheduledAt, arguments);
        jobRunEntity.addJobRunStateTransition(
                new JobRunStateTransitionEmbeddable(JobRunState.SCHEDULED, scheduledAt));
        return jobRunRepository.save(jobRunEntity).toJobRun();
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
        return Objects.requireNonNull(jobTriggerRepository.save(entity).toTrigger());
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
        return k8sJobRepository.findBy(
                Example.of(new K8sJobEntity(jobRunId, null)),
                entity -> entity.first().map(K8sJobEntity::k8sJobName));
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
        jobRepository
                .findById(jobId)
                .ifPresent(
                        jobEntity -> {
                            jobEntity.setEnabled(isEnabled);
                            jobRepository.save(jobEntity);
                        });
    }

    private static @NonNull Supplier<ResourceNotFoundException> throwJobRunNotFound(int jobRunId) {
        return ResourceNotFoundException.supplierWithMessage(
                "No JobRunEntity with id {} found", jobRunId);
    }
}
