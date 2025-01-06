package ch.ubique.backend.job.data.service;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.ubique.backend.core.exception.ResourceNotFoundException;
import ch.ubique.backend.core.file.ByteFile;
import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.data.repository.*;
import ch.ubique.backend.job.model.*;
import ch.ubique.backend.job.model.entity.*;
import ch.ubique.backend.job.model.input.JobRunInputDeclaration;
import ch.ubique.backend.job.model.properties.*;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.triggers.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.apache.commons.lang3.Range;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
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
            final LoggingEventRepository loggingEventRepository) {
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
    }

    @Transactional(readOnly = true)
    public Optional<JobClassAndId> findJobById(final int id) {
        return jobRepository.findById(id).map(JobEntity::toRecord);
    }

    @Transactional(readOnly = true)
    public Optional<JobClassAndId> findJobByCanonicalClassName(final String clazz) {
        return jobRepository.findByCanonicalClassName(clazz).map(JobEntity::toRecord);
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
    public Collection<JobRunStateTransition> findJobRunStateTransitionsByJobRunId(
            final int jobRunId) {
        return jobRunStateTransitionRepository
                .findAllByJobRunId(jobRunId)
                .map(JobRunStateTransitionEntity::toJobRunStateTransition)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobClassAndId> findJobs() {
        return jobRepository.findAllByOrderById().map(JobEntity::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public List<JobClassAndId> findJobsInclude(final List<Integer> includeJobIds) {
        return jobRepository
                .findAllByIdInOrderById(includeJobIds)
                .map(JobEntity::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobClassAndId> findJobsExclude(final List<Integer> excludeJobIds) {
        return jobRepository
                .findAllByIdNotInOrderById(excludeJobIds)
                .map(JobEntity::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<JobClassAndId> findJobsPage(final Pageable pageable) {
        return jobRepository.findAll(pageable).map(JobEntity::toRecord);
    }

    private Predicate buildJobRunsPredicate(
            final List<Integer> jobIds,
            final JobRunState state,
            final Optional<Range<Instant>> dateTimeRange,
            final Root<JobRunLatestStateViewEntity> root,
            final CriteriaBuilder criteriaBuilder) {

        final var predicates = new ArrayList<Predicate>();

        if (!jobIds.isEmpty()) {
            final var jobIdInConstraint = root.get("jobId").in(jobIds);
            predicates.add(jobIdInConstraint);
        }

        if (state != null) {
            predicates.add(criteriaBuilder.equal(root.get("latestState"), state));
        }

        if (dateTimeRange.isPresent()) {
            final var range = dateTimeRange.get();
            predicates.add(
                    criteriaBuilder.between(
                            root.get("startedAt"), range.getMinimum(), range.getMaximum()));
        }

        return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    }

    @Transactional(readOnly = true)
    public Page<JobRun> findJobRuns(
            final Pageable pageable,
            @NotNull final List<Integer> includeJobIds,
            final JobRunState state,
            final Optional<Range<Instant>> fromToRange) {
        return jobRunLatestStateViewRepository
                .findAll(
                        (root, query, criteriaBuilder) ->
                                buildJobRunsPredicate(
                                        includeJobIds, state, fromToRange, root, criteriaBuilder),
                        pageable)
                .map(JobRunLatestStateViewEntity::toJobRun);
    }

    @Transactional
    public JobClassAndId findOrInsertJob(final Class<? extends Job> jobClass) {
        final var jobClassCanonicalName = jobClass.getCanonicalName();
        return jobRepository
                .findByCanonicalClassName(jobClassCanonicalName)
                // uncomment the following line if you ever add additional fields to JobRunEntity
                // apart from the canonical class name
                // .map(entity -> jobRepository.save(entity.with(jobClassCanonicalName)))
                .orElseGet(() -> jobRepository.save(new JobEntity(jobClassCanonicalName)))
                .toRecord();
    }

    @Transactional
    public JobRun updateJobRunRunnerKeyAndArguments(
            @NotNull final JobRun jobRun,
            @NotNull final JobRunnerKey runnerKey,
            final JobRunArguments arguments) {
        return jobRunRepository
                .findById(jobRun.id())
                .map(
                        entity -> {
                            entity.setRunnerKey(runnerKey);
                            entity.setJobRunArguments(arguments);
                            return jobRunRepository.save(entity);
                        })
                .orElseThrow(
                        ResourceNotFoundException.supplierWithMessage(
                                "No JobRunEntity with id {} found", jobRun.id()))
                .toJobRun();
    }

    @Transactional
    public JobRun startJobRun(
            final int jobRunId,
            @NotNull final JobRunState jobRunState,
            @NotNull final Instant startedAt) {
        return jobRunRepository
                .findById(jobRunId)
                .map(
                        entity -> {
                            entity.setStartedAt(startedAt);
                            entity.addJobRunStateTransition(
                                    new JobRunStateTransitionEmbeddable(jobRunState, startedAt));
                            return jobRunRepository.save(entity);
                        })
                .orElseThrow(
                        ResourceNotFoundException.supplierWithMessage(
                                "No JobRunEntity with id {} found", jobRunId))
                .toJobRun();
    }

    @Transactional
    public void updateJobRunState(
            final int jobRunId,
            final @NotNull JobRunState jobRunState,
            final @NotNull Instant transitionedAt) {
        final var jobRunEntity =
                jobRunRepository
                        .findById(jobRunId)
                        .orElseThrow(
                                ResourceNotFoundException.supplierWithMessage(
                                        "No JobRunEntity with id {} found", jobRunId));

        if (jobRunEntity.finishedAt() != null && jobRunEntity.latestState().isRunning()) {
            // finishedAt should not be set for jobs in Running state
            jobRunEntity.setFinishedAt(null);
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
    public void deleteJob(final int jobId) {
        jobTriggerRepository.deleteAllByJobId(jobId);
        jobPropertyRepository.deleteAllByJobId(jobId);
        jobRunInputRepository.deleteAllByJobId(jobId);
        jobRunRepository.deleteAllByJobId(jobId);
        jobRepository.deleteById(jobId);
    }

    @Transactional
    public void insertJobRunInput(final JobRun jobRun, final ByteFile input) {
        jobRunInputRepository.save(JobRunInputEntity.from(jobRun, input));
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
    public Collection<LoggingEvent> findLogsForJobRun(
            final int jobRunId, final long skipLines, final int pageSize) {
        return loggingEventRepository
                .findByJobRunId(jobRunId, skipLines, pageSize)
                .map(LoggingEventEntity::toLoggingEvent)
                .toList();
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
            final int jobId, final int daysToKeep, final int chunkSize) {
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
        return jobRunRepository
                .findAllByJobIdAndFinishedAtBeforeOrderByFinishedAt(
                        jobId, finishedBefore, PageRequest.of(0, chunkSize))
                .map(JobRunEntity::toJobRun)
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
            @PositiveOrZero final int jobVersion,
            @NotNull final JobRunnerKey runnerKey,
            final JobRunArguments arguments) {
        final var scheduledAt = Instant.now();
        final var jobRunEntity =
                new JobRunEntity(jobId, jobVersion, runnerKey, scheduledAt, null, arguments);
        jobRunEntity.addJobRunStateTransition(
                new JobRunStateTransitionEmbeddable(JobRunState.SCHEDULED, scheduledAt));
        return jobRunRepository.save(jobRunEntity).toJobRun();
    }

    @Transactional
    public void saveTrigger(final Trigger trigger) {
        jobTriggerRepository
                .findById(trigger.id())
                .ifPresentOrElse(
                        entity -> jobTriggerRepository.save(entity.with(trigger)),
                        () -> jobTriggerRepository.save(new JobTriggerEntity(trigger)));
    }

    @Transactional
    public void deleteTriggerForJob(final int jobId) {
        jobTriggerRepository.deleteAllByJobId(jobId);
    }

    @Transactional
    public void deleteJob(final Trigger trigger) {
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
}
