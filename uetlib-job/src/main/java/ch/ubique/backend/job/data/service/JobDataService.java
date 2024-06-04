package ch.ubique.backend.job.data.service;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.ubique.backend.core.exception.ResourceNotFoundException;
import ch.ubique.backend.core.file.ByteFile;
import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.data.repository.*;
import ch.ubique.backend.job.model.*;
import ch.ubique.backend.job.model.entity.*;
import ch.ubique.backend.job.model.properties.*;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.triggers.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class JobDataService {

    private final JobRepository jobRepository;
    private final JobTriggerRepository jobTriggerRepository;
    private final JobPropertyRepository jobPropertyRepository;
    private final JobRunRepository jobRunRepository;
    private final JobRunInputRepository jobRunInputRepository;
    private final JobRunDatabaseSubmissionRepository jobRunDatabaseSubmissionRepository;
    private final LoggingEventRepository loggingEventRepository;

    @PersistenceContext private EntityManager entityManager;

    public JobDataService(
            final JobRepository jobRepository,
            final JobTriggerRepository jobTriggerRepository,
            final JobPropertyRepository jobPropertyRepository,
            final JobRunRepository jobRunRepository,
            final JobRunInputRepository jobRunInputRepository,
            final JobRunDatabaseSubmissionRepository jobRunDatabaseSubmissionRepository,
            final LoggingEventRepository loggingEventRepository) {
        this.jobRepository = jobRepository;
        this.jobTriggerRepository = jobTriggerRepository;
        this.jobPropertyRepository = jobPropertyRepository;
        this.jobRunRepository = jobRunRepository;
        this.jobRunInputRepository = jobRunInputRepository;
        this.jobRunDatabaseSubmissionRepository = jobRunDatabaseSubmissionRepository;
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
        return jobRunRepository
                .findFirstByJobIdAndStateOrderByStartedAtDesc(jobId, state)
                .map(JobRunEntity::toJobRun);
    }

    @Transactional(readOnly = true)
    public Optional<JobRun> findLatestJobRunByStates(
            final int jobId, final Collection<JobRunState> states) {
        return jobRunRepository
                .findFirstByJobIdAndStateInOrderByStartedAtDesc(jobId, states)
                .map(JobRunEntity::toJobRun);
    }

    @Transactional(readOnly = true)
    public List<JobClassAndId> findJobs() {
        return jobRepository.findAllByOrderById().stream().map(JobEntity::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public List<JobClassAndId> findJobsInclude(final List<Integer> includeJobIds) {
        return jobRepository.findAllByIdInOrderById(includeJobIds).stream()
                .map(JobEntity::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobClassAndId> findJobsExclude(final List<Integer> excludeJobIds) {
        return jobRepository.findAllByIdNotInOrderById(excludeJobIds).stream()
                .map(JobEntity::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<JobClassAndId> findJobsPage(final Integer page, final int pageSize) {
        final var pageRequest =
                Optional.ofNullable(page)
                        .map(p -> (Pageable) PageRequest.of(p, pageSize))
                        // pre v1.5 does not send page and pageSize parameter, need to fall back to
                        // unpaged
                        .orElseGet(Pageable::unpaged);
        return jobRepository.findAllByOrderById(pageRequest).map(JobEntity::toRecord);
    }

    private Predicate getPredicate(
            final List<Integer> jobIds,
            final JobRunState state,
            final Optional<Pair<Instant, Instant>> dateTimeRange,
            final Root<JobRunEntity> root,
            final CriteriaBuilder criteriaBuilder) {
        final var predicates = new ArrayList<Predicate>();
        if (!jobIds.isEmpty()) {
            final var jobIdInConstraint = root.get("jobId").in(jobIds);
            predicates.add(jobIdInConstraint);
        }
        if (state != null) {
            predicates.add(criteriaBuilder.equal(root.get("state"), state));
        }
        if (dateTimeRange.isPresent()) {
            final var range = dateTimeRange.get();
            predicates.add(
                    criteriaBuilder.between(
                            root.get("startedAt"), range.getLeft(), range.getRight()));
        }
        return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    }

    public Page<JobRun> findJobRunsUsingCriteriaBuilder(
            final int page,
            final int perPage,
            @NotNull final List<Integer> includeJobIds,
            final JobRunState state,
            final Optional<Pair<Instant, Instant>> fromToRange) {
        final var request = PageRequest.of(page, perPage);
        return jobRunRepository
                .findAll(
                        (root, query, criteriaBuilder) -> {
                            query.orderBy(criteriaBuilder.desc(root.get("startedAt")));
                            return getPredicate(
                                    includeJobIds, state, fromToRange, root, criteriaBuilder);
                        },
                        request)
                .map(JobRunEntity::toJobRun);
    }

    @Transactional
    @SuppressWarnings("java:S6809")
    public JobClassAndId findOrInsertJob(final Class<? extends Job> jobClass) {
        return jobRepository
                .findByCanonicalClassName(jobClass.getCanonicalName())
                .map(JobEntity::toRecord)
                .orElseGet(() -> saveJob(jobClass));
    }

    @Transactional
    public JobClassAndId saveJob(final Class<? extends Job> jobClass) {
        return jobRepository.save(new JobEntity(jobClass.getCanonicalName())).toRecord();
    }

    @Transactional
    public JobRun saveJobRun(final JobRun jobRun) {
        return jobRunRepository.save(new JobRunEntity(jobRun)).toJobRun();
    }

    @Transactional(readOnly = true)
    public Optional<JobRun> findJobRunById(final int jobRunId) {
        return jobRunRepository.findById(jobRunId).map(JobRunEntity::toJobRun);
    }

    @Transactional
    public void delete(final JobClassAndId job) {
        jobTriggerRepository.deleteAllByJobId(job.id());
        jobPropertyRepository.deleteAllByJobId(job.id());
        jobRunInputRepository.deleteAllByJobId(job.id());
        jobRunRepository.deleteAllByJobId(job.id());
        jobRepository.deleteById(job.id());
    }

    @Transactional
    public void insertJobRunInput(final JobRun jobRun, final ByteFile input) {
        jobRunInputRepository.save(JobRunInputEntity.from(jobRun, input));
    }

    @Transactional(readOnly = true)
    public Collection<JobRunInput> findJobRunInputs(final int runId) {
        return jobRunInputRepository.findAllByRunId(runId);
    }

    @Transactional(readOnly = true)
    public Stream<LoggingEvent> findLogsForJobRun(
            final int jobRunId, final long skipLines, final int pageSize) {
        return loggingEventRepository.findByJobRunId(jobRunId, skipLines, pageSize).stream()
                .map(LoggingEventEntity::toLoggingEvent);
    }

    @Transactional(readOnly = true)
    public Collection<JobProperty<Object>> getProperties(final int jobId) {
        return jobPropertyRepository.findAllByJobId(jobId).stream()
                .map(JobPropertyEntity::toProperty)
                .filter(JobProperty::isValuePresent)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public void saveProperties(final int jobId, final Collection<JobProperty<?>> properties) {

        // Save all where value is set
        jobPropertyRepository.saveAll(
                properties.stream()
                        .filter(JobProperty::isValuePresent)
                        .map(property -> JobPropertyEntity.from(jobId, property))
                        .toList());

        // Delete all where value is unset
        jobPropertyRepository.deleteAll(
                properties.stream()
                        .filter(property -> !property.isValuePresent())
                        .map(property -> JobPropertyEntity.from(jobId, property))
                        .toList());
    }

    /**
     * WARN: IF YOU DO NOT KNOW WHAT YOU ARE DOING, ONLY CALL THIS METHOD VIA {@link
     * ch.ubique.backend.job.service.JobService#getProperties}.
     *
     * <p>This method only retrieves properties are they are in the DB and ignores fixed
     * JobProperties
     */
    @Transactional(readOnly = true)
    public <T> JobProperty<T> getProperty(
            final Integer jobId, final JobPropertyDefinition<T> definition) {
        return jobPropertyRepository
                .findByJobIdAndKey(jobId, definition.key())
                .map(JobPropertyEntity::getValue)
                .map(definition::createPropertyFromString)
                .orElseGet(() -> definition.createProperty(null));
    }

    @Transactional(readOnly = true)
    public List<JobRun> findCleanableRuns(
            final int jobId, final int daysToKeep, final int chunkSize) {
        final var finishedBefore =
                jobRunRepository
                        .findFirstByStateAndFinishedAtIsNotNullAndJobIdOrderByFinishedAtDesc(
                                JobRunState.DONE, jobId)
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
        loggingEventRepository.deleteLogsForJobRunId(jobRunId);
    }

    @Transactional
    public void clearUnknownJobProperties() {
        final var jobPropertyNames =
                JobPropertyDefinitions.values().stream().map(JobPropertyDefinition::key).toList();
        jobPropertyRepository.deleteAllByKeyNotIn(jobPropertyNames);
    }

    @Transactional(readOnly = true)
    public Collection<Trigger> getTriggers(final int jobId) {
        return jobTriggerRepository.findAllByJobId(jobId).stream()
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
        return jobRunRepository
                .save(
                        new JobRunEntity(
                                jobId,
                                jobVersion,
                                JobRunState.SCHEDULED,
                                runnerKey,
                                Instant.now(),
                                null,
                                arguments))
                .toJobRun();
    }

    @Transactional
    public void saveTrigger(final Trigger trigger) {
        jobTriggerRepository.save(new JobTriggerEntity(trigger));
    }

    @Transactional
    public void deleteTriggerForJob(final int jobId) {
        jobTriggerRepository.deleteAllByJobId(jobId);
    }

    @Transactional
    public void delete(final Trigger trigger) {
        jobTriggerRepository.deleteById(trigger.id());
    }

    @Transactional
    public Collection<Trigger> insertTriggers(
            final int jobId, final Collection<TriggerDefinition> triggerDefinitions) {
        final var entities =
                triggerDefinitions.stream().map(def -> new JobTriggerEntity(jobId, def)).toList();
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

    @Transactional(readOnly = true)
    public Collection<JobRun> findAllCurrentRunnerJobRunsWithoutFinishedTimestamp() {
        return jobRunRepository.findAllByRunnerKeyAndFinishedAtIsNull(JobRunnerKey.CURRENT).stream()
                .map(JobRunEntity::toJobRun)
                .toList();
    }

    @Transactional(readOnly = true)
    public Collection<JobRun> findJobRunsFinishedAfter(final Instant instant) {
        return jobRunRepository.findAllByFinishedAtIsNotNullAndFinishedAtAfter(instant).stream()
                .map(JobRunEntity::toJobRun)
                .toList();
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
        return jobRunDatabaseSubmissionRepository.findAllByExecutedAtIsNull().stream()
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
}
