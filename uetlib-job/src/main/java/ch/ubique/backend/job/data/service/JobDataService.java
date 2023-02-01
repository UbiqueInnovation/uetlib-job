package ch.ubique.backend.job.data.service;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.ubique.backend.core.exception.ResourceNotFoundException;
import ch.ubique.backend.job.data.repository.JobPropertyRepository;
import ch.ubique.backend.job.data.repository.JobRepository;
import ch.ubique.backend.job.data.repository.JobRunInputRepository;
import ch.ubique.backend.job.data.repository.JobRunRepository;
import ch.ubique.backend.job.data.repository.LoggingEventRepository;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobPropertyKey;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunInput;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.entity.JobEntity;
import ch.ubique.backend.job.model.entity.JobPropertyEntity;
import ch.ubique.backend.job.model.entity.JobRunEntity;
import ch.ubique.backend.job.model.entity.JobRunInputEntity;
import ch.ubique.backend.job.model.entity.LoggingEventEntity;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobDataService {

    private final JobRepository jobRepository;
    private final JobPropertyRepository jobPropertyRepository;
    private final JobRunRepository jobRunRepository;
    private final JobRunInputRepository jobRunInputRepository;

    private final LoggingEventRepository loggingEventRepository;

    @Value("${job.startup.cleanUpBusyRuns:true}")
    private boolean cleanUpBusyRuns;

    public JobDataService(
            final JobRepository jobRepository,
            final JobPropertyRepository jobPropertyRepository,
            final JobRunRepository jobRunRepository,
            final JobRunInputRepository jobRunInputRepository,
            final LoggingEventRepository loggingEventRepository) {
        this.jobRepository = jobRepository;
        this.jobPropertyRepository = jobPropertyRepository;
        this.jobRunRepository = jobRunRepository;
        this.jobRunInputRepository = jobRunInputRepository;
        this.loggingEventRepository = loggingEventRepository;
    }

    @PostConstruct
    @Transactional
    public void init() {
        if (cleanUpBusyRuns) {
            cleanUpBusyRuns();
        }
    }

    @Transactional
    public void cleanUpBusyRuns() {
        final var now = Instant.now();
        jobRunRepository.saveAllAndFlush(
                jobRunRepository.findAllByStateAndFinishedAtIsNull(JobRunState.BUSY).stream()
                        .map(JobRunEntity.class::cast)
                        .map(
                                jobRun -> {
                                    jobRun.setFinishedAt(now);
                                    jobRun.setState(JobRunState.UNFINISHED);
                                    return jobRunRepository.save(jobRun);
                                })
                        .toList());
    }

    @Transactional(readOnly = true)
    public Optional<Job> findJobByClazz(String clazz) {
        return jobRepository.findByClazz(clazz).map(JobEntity::toJob);
    }

    @Transactional(readOnly = true)
    public Optional<Job> findJobById(int id) {
        return jobRepository.findById(id).map(JobEntity::toJob);
    }

    @Transactional
    public Job updateCronExpression(final int id, final String cronExpression) {
        return jobRepository
                .findById(id)
                .map(
                        entity -> {
                            entity.setCronExpression(cronExpression);
                            return jobRepository.save(entity).toJob();
                        })
                .orElseThrow(
                        ResourceNotFoundException.throwWithMessage(
                                "No JobEntity with id {0} found", id));
    }

    @Transactional(readOnly = true)
    public Optional<JobRun> findLatestJobRun(final int jobId) {
        return jobRunRepository.findFirstByJobIdOrderByStartedAtDesc(jobId);
    }

    public Optional<JobRun> findLatestJobRunByState(int jobId, JobRunState state) {
        return jobRunRepository.findFirstByJobIdAndStateOrderByStartedAtDesc(jobId, state);
    }

    public Optional<JobRun> findLatestJobRunByStates(int jobId, JobRunState... states) {
        return jobRunRepository.findFirstByJobIdAndStateInOrderByStartedAtDesc(
                jobId, List.of(states));
    }

    @Transactional(readOnly = true)
    public List<Job> findJobs() {
        return jobRepository.findAllByOrderById().stream().map(JobEntity::toJob).toList();
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
    public Job registerJob(final Job job) {
        return findJobByClazz(job.clazzName())
                .orElseGet(
                        () -> {
                            final var entity = JobEntity.fromJob(job);
                            return jobRepository.save(entity).toJob();
                        });
    }

    @Transactional
    public JobRunEntity saveJobRun(final JobRun jobRun) {
        if (!(jobRun instanceof JobRunEntity jobRunEntity)) {
            throw new ClassCastException(
                    "jobRun cannot be instance of class to JobRunEntity, but is of class"
                            + jobRun.getClass());
        }
        return jobRunRepository.save(jobRunEntity);
    }

    public JobRunEntity newRunInstance(final Job job) {
        final var entity = new JobRunEntity();
        entity.setJobId(job.id());
        return entity;
    }

    public Optional<JobRun> findJobRunById(final int jobRunId) {
        return jobRunRepository.findById(jobRunId);
    }

    public void delete(Job job) {
        jobRepository.delete(JobEntity.fromJob(job));
    }

    public void insertJobRunInput(JobRunInput jobRunInput) {
        if (!(jobRunInput instanceof JobRunInputEntity jobRunInputEntity)) {
            throw new ClassCastException(
                    "jobRun cannot be instance of class to JobRunInputEntity, but is of class"
                            + jobRunInput.getClass());
        }
        jobRunInputRepository.save(jobRunInputEntity);
    }

    public Collection<JobRunInput> findJobRunInputs(int runId) {
        return jobRunInputRepository.findAllByRunId(runId);
    }

    public List<LoggingEvent> findLogsForJobRun(final int jobRunId) {
        return loggingEventRepository.findByJobRunId(String.valueOf(jobRunId)).stream()
                .map(LoggingEventEntity::toLoggingEvent)
                .toList();
    }

    public void saveProperty(final int jobId, final JobPropertyKey key, final String value) {
        jobPropertyRepository.save(
                new JobPropertyEntity().setJobId(jobId).setKey(key).setValue(value));
    }

    public Map<JobPropertyKey, String> getProperties(final int jobId) {
        return jobPropertyRepository.findAllByJobId(jobId).stream()
                .collect(Collectors.toMap(JobPropertyEntity::getKey, JobPropertyEntity::getValue));
    }

    public void clearProperties(final int jobId) {
        jobPropertyRepository.deleteAllByJobId(jobId);
    }

    public void saveProperties(final int jobId, final Map<JobPropertyKey, String> properties) {
        jobPropertyRepository.saveAll(
                properties.entrySet().stream()
                        .map(
                                entry ->
                                        new JobPropertyEntity()
                                                .setJobId(jobId)
                                                .setKey(entry.getKey())
                                                .setValue(entry.getValue()))
                        .toList());
    }

    public String getProperty(final Integer jobId, final JobPropertyKey key) {
        return jobPropertyRepository
                .findByJobIdAndKey(jobId, key)
                .map(JobPropertyEntity::getValue)
                .orElse(key.getDefaultValue());
    }
}
