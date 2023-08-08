package ch.ubique.backend.job.data.service;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.data.repository.*;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunInput;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.entity.*;
import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinition;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.triggers.Trigger;
import ch.ubique.backend.job.model.triggers.TriggerDefinition;

import com.pivovarit.function.ThrowingFunction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class JobDataService {

    private final JobRepository jobRepository;
    private final JobTriggerRepository jobTriggerRepository;
    private final JobPropertyRepository jobPropertyRepository;
    private final JobRunRepository jobRunRepository;
    private final JobRunInputRepository jobRunInputRepository;
    private final LoggingEventRepository loggingEventRepository;

    @PersistenceContext private EntityManager entityManager;

    @Value("${job.startup.setBusyRunsToUnfinished:${job.startup.cleanUpBusyRuns:true}}")
    private boolean setBusyRunsToUnfinished;

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

    @Transactional
    public void setBusyRunsToUnfinished() {
        if (!setBusyRunsToUnfinished) {
            return;
        }

        final var now = Instant.now();
        jobRunRepository.saveAllAndFlush(
                jobRunRepository.findAllByStateAndFinishedAtIsNull(JobRunState.BUSY).stream()
                        .filter(
                                // Only for busy jobs with Runner = CURRENT
                                jobRun ->
                                        JobRunnerKey.CURRENT
                                                == getProperty(
                                                                jobRun.jobId(),
                                                                JobPropertyDefinitions.RUNNER)
                                                        .value())
                        .map(
                                jobRun ->
                                        saveJobRun(
                                                jobRun.modify()
                                                        .finishedAt(now)
                                                        .state(JobRunState.UNFINISHED)
                                                        .toJobRun()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public Optional<JobClassAndId> findJobById(final int id) {
        return jobRepository.findById(id).map(JobEntity::toRecord);
    }

    @Transactional(readOnly = true)
    public Optional<JobClassAndId> findJobByCanonicalClassName(final String clazz) {
        return jobRepository.findByClazz(clazz).map(JobEntity::toRecord);
    }

    @Transactional(readOnly = true)
    public Optional<JobRun> findLatestJobRun(final int jobId) {
        return jobRunRepository.findFirstByJobIdOrderByStartedAtDesc(jobId);
    }

    public Optional<JobRun> findLatestJobRunByState(final int jobId, final JobRunState state) {
        return jobRunRepository.findFirstByJobIdAndStateOrderByStartedAtDesc(jobId, state);
    }

    public Optional<JobRun> findLatestJobRunByStates(final int jobId, final JobRunState... states) {
        return jobRunRepository.findFirstByJobIdAndStateInOrderByStartedAtDesc(
                jobId, List.of(states));
    }

    @Transactional(readOnly = true)
    public List<JobClassAndId> findJobs() {
        return jobRepository.findAllByOrderById().stream().map(JobEntity::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public Page<JobRun> findJobRuns(
            final int page, final int pageSize, final Integer jobId, final JobRunState state) {
        final var pageRequest = PageRequest.of(page, pageSize);
        if (jobId != null) {
            if (state != null) {
                return jobRunRepository.findAllByJobIdAndStateOrderByStartedAtDesc(
                        jobId, state, pageRequest);
            }
            return jobRunRepository.findAllByJobIdOrderByStartedAtDesc(jobId, pageRequest);
        }
        if (state != null) {
            return jobRunRepository.findAllByStateOrderByStartedAtDesc(state, pageRequest);
        }
        return jobRunRepository.findAllByOrderByStartedAtDesc(pageRequest);
    }

    @Transactional
    public JobClassAndId registerJob(final Class<? extends Job> jobClass) {
        final var canonicalClassName = jobClass.getCanonicalName();
        return findJobByCanonicalClassName(canonicalClassName)
                .orElseGet(() -> jobRepository.save(new JobEntity(canonicalClassName)).toRecord());
    }

    @Transactional
    public JobRunEntity saveJobRun(final JobRun jobRun) {
        return jobRunRepository.save(new JobRunEntity(jobRun));
    }

    @Transactional(readOnly = true)
    public Optional<JobRun> findJobRunById(final int jobRunId) {
        return jobRunRepository.findById(jobRunId);
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
    public List<LoggingEvent> findLogsForJobRun(final int jobRunId) {
        return loggingEventRepository.findByJobRunId(String.valueOf(jobRunId)).stream()
                .map(LoggingEventEntity::toLoggingEvent)
                .toList();
    }

    @Transactional
    public <T> void saveProperty(final int jobId, final JobProperty<T> property) {
        final var entity = JobPropertyEntity.from(jobId, property);
        if (StringUtils.isBlank(property.encodedValue())) {
            jobPropertyRepository.delete(entity);
        } else {
            jobPropertyRepository.save(entity);
        }
    }

    @Transactional(readOnly = true)
    public Collection<JobProperty<Object>> getProperties(final int jobId) {
        return jobPropertyRepository.findAllByJobId(jobId).stream()
                .map(JobPropertyEntity::toProperty)
                .filter(JobProperty::isValuePresent)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public void clearProperties(final int jobId) {
        jobPropertyRepository.deleteAllByJobId(jobId);
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
                .map(entity -> definition.createPropertyFromString(entity.getValue()))
                .orElseGet(() -> definition.createProperty(null));
    }

    @Transactional(readOnly = true)
    public List<JobRun> findCleanableRuns(
            final int jobId, final int daysToKeep, final int chunkSize) {
        // JPA will throw an error when attempting to return JobRun from the select statement below
        // --> need to cast
        return jobRunRepository
                .findAllByJobIdAndFinishedAt(jobId, daysToKeep, chunkSize)
                .map(JobRun.class::cast)
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
                .map(ThrowingFunction.unchecked(JobTriggerEntity::toTrigger))
                .toList();
    }

    public JobRun createJobRun(final int jobId, final int jobVersion) {
        return jobRunRepository
                .save(new JobRunEntity(jobId, jobVersion, JobRunState.BUSY, Instant.now(), null))
                .toJobRun();
    }

    public void saveTrigger(final Trigger trigger) {
        jobTriggerRepository.save(new JobTriggerEntity(trigger)).toTrigger();
    }

    public void deleteTriggerForJob(final int jobId) {
        jobTriggerRepository.deleteByJobId(jobId);
    }

    public Collection<Trigger> insertTriggers(
            final int jobId, final Collection<TriggerDefinition> triggerDefinitions) {
        final var entities =
                triggerDefinitions.stream().map(def -> new JobTriggerEntity(jobId, def)).toList();
        return jobTriggerRepository.saveAll(entities).stream()
                .map(JobTriggerEntity::toTrigger)
                .toList();
    }

    public Trigger insertTrigger(final int jobId, final TriggerDefinition triggerDefinition) {
        final var entity = new JobTriggerEntity(jobId, triggerDefinition);
        return jobTriggerRepository.save(entity).toTrigger();
    }
}
