package ch.ubique.backend.job.data.service;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.data.repository.*;
import ch.ubique.backend.job.model.*;
import ch.ubique.backend.job.model.entity.*;
import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinition;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.triggers.Trigger;
import ch.ubique.backend.job.model.triggers.TriggerDefinition;

import com.pivovarit.function.ThrowingFunction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class JobDataService {

    private final JobRepository jobRepository;
    private final JobTriggerRepository jobTriggerRepository;
    private final JobPropertyRepository jobPropertyRepository;
    private final JobRunRepository jobRunRepository;
    private final JobRunInputRepository jobRunInputRepository;
    private final LoggingEventRepository loggingEventRepository;

    @PersistenceContext private EntityManager entityManager;

    public JobDataService(
            final JobRepository jobRepository,
            final JobTriggerRepository jobTriggerRepository,
            final JobPropertyRepository jobPropertyRepository,
            final JobRunRepository jobRunRepository,
            final JobRunInputRepository jobRunInputRepository,
            final LoggingEventRepository loggingEventRepository) {
        this.jobRepository = jobRepository;
        this.jobTriggerRepository = jobTriggerRepository;
        this.jobPropertyRepository = jobPropertyRepository;
        this.jobRunRepository = jobRunRepository;
        this.jobRunInputRepository = jobRunInputRepository;
        this.loggingEventRepository = loggingEventRepository;
    }

    @Transactional(readOnly = true)
    public Optional<JobClassAndId> findJobById(final int id) {
        return jobRepository.findById(id).map(JobEntity::toRecord);
    }

    @SuppressWarnings("unused") // used in swisstopo-backend::LastLayerUpdateService
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
    public Optional<JobRun> findLatestJobRunByStates(final int jobId, final JobRunState... states) {
        return jobRunRepository
                .findFirstByJobIdAndStateInOrderByStartedAtDesc(jobId, List.of(states))
                .map(JobRunEntity::toJobRun);
    }

    @Transactional(readOnly = true)
    public List<JobClassAndId> findJobs() {
        return jobRepository.findAllByOrderById().stream().map(JobEntity::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public List<JobClassAndId> findJobsInclude(List<Integer> includeJobIds) {
        return jobRepository.findAllByIdInOrderById(includeJobIds).stream()
                .map(JobEntity::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobClassAndId> findJobsExclude(List<Integer> excludeJobIds) {
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
                        .orElse(Pageable.unpaged());
        return jobRepository.findAllByOrderById(pageRequest).map(JobEntity::toRecord);
    }

    @Transactional(readOnly = true)
    public Page<JobRun> findJobRuns(
            final int page,
            final int pageSize,
            @NotNull final Collection<Integer> jobIds,
            final JobRunState state) {
        final var pageRequest = PageRequest.of(page, pageSize);
        if (jobIds == null || !jobIds.isEmpty()) {
            if (state != null) {
                return jobRunRepository
                        .findAllByJobIdInAndStateOrderByStartedAtDesc(jobIds, state, pageRequest)
                        .map(JobRunEntity::toJobRun);
            }
            return jobRunRepository
                    .findAllByJobIdInOrderByStartedAtDesc(jobIds, pageRequest)
                    .map(JobRunEntity::toJobRun);
        }
        if (state != null) {
            return jobRunRepository
                    .findAllByStateOrderByStartedAtDesc(state, pageRequest)
                    .map(JobRunEntity::toJobRun);
        }
        return jobRunRepository
                .findAllByOrderByStartedAtDesc(pageRequest)
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
    public JobRunEntity saveJobRun(final JobRun jobRun) {
        return jobRunRepository.save(new JobRunEntity(jobRun));
    }

    @Transactional(readOnly = true)
    public Optional<JobRun> findJobRunById(final int jobRunId) {
        return jobRunRepository.findById(jobRunId).map(JobRunEntity::toJobRun);
    }

    @Transactional
    public void delete(final JobClassAndId job) {
        jobRepository.delete(new JobEntity(job));
    }

    @Transactional
    public void insertJobRunInput(final JobRunInput jobRunInput) {
        if (!(jobRunInput instanceof final JobRunInputEntity jobRunInputEntity)) {
            throw new ClassCastException(
                    "jobRun cannot be instance of class to JobRunInputEntity, but is of class"
                            + jobRunInput.getClass());
        }
        jobRunInputRepository.save(jobRunInputEntity);
    }

    @Transactional(readOnly = true)
    public Collection<JobRunInput> findJobRunInputs(final int runId) {
        return jobRunInputRepository.findAllByRunId(runId);
    }

    @Transactional(readOnly = true)
    public Stream<LoggingEvent> findLogsForJobRun(
            final int jobRunId, final long skipLines, final int pageSize) {
        return loggingEventRepository
                .findByJobRunId(String.valueOf(jobRunId), skipLines, pageSize)
                .stream()
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
        loggingEventRepository.deleteLogsForJobRunId(String.valueOf(jobRunId));
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
                .map(ThrowingFunction.unchecked(JobTriggerEntity::toTrigger))
                .toList();
    }

    @Transactional
    public JobRun createJobRun(
            final int jobId, final int jobVersion, final JobRunArguments arguments) {
        return jobRunRepository
                .save(
                        new JobRunEntity(
                                jobId,
                                jobVersion,
                                JobRunState.SCHEDULED,
                                Instant.now(),
                                null,
                                arguments))
                .toJobRun();
    }

    @Transactional
    public void saveTrigger(final Trigger trigger) {
        jobTriggerRepository.save(new JobTriggerEntity(trigger)).toTrigger();
    }

    @Transactional
    public void deleteTriggerForJob(final int jobId) {
        jobTriggerRepository.deleteByJobId(jobId);
    }

    @Transactional
    public void delete(final Trigger trigger) {
        jobTriggerRepository.delete(new JobTriggerEntity(trigger));
    }

    @Transactional
    public Collection<Trigger> insertTriggers(
            final int jobId, final Collection<TriggerDefinition> triggerDefinitions) {
        final var entities =
                triggerDefinitions.stream().map(def -> new JobTriggerEntity(jobId, def)).toList();
        return jobTriggerRepository.saveAll(entities).stream()
                .map(JobTriggerEntity::toTrigger)
                .toList();
    }

    @Transactional
    public Trigger insertTrigger(final int jobId, final TriggerDefinition triggerDefinition) {
        final var entity = new JobTriggerEntity(jobId, triggerDefinition);
        return jobTriggerRepository.save(entity).toTrigger();
    }

    @Transactional(readOnly = true)
    public Collection<JobRun> findJobRunsWithoutFinishedTimestamp() {
        return jobRunRepository.findAllByFinishedAtIsNull().stream()
                .map(JobRunEntity::toJobRun)
                .toList();
    }

    @Transactional(readOnly = true)
    public Collection<JobRun> findJobRunsFinishedAfter(final Instant instant) {
        return jobRunRepository.findAllByFinishedAtIsNotNullAndFinishedAtAfter(instant).stream()
                .map(JobRunEntity::toJobRun)
                .toList();
    }
}
