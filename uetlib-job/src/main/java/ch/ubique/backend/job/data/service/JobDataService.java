package ch.ubique.backend.job.data.service;

import ch.ubique.backend.core.exception.ResourceNotFoundException;
import ch.ubique.backend.job.data.repository.JobRepository;
import ch.ubique.backend.job.data.repository.JobRunRepository;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.entity.JobEntity;
import ch.ubique.backend.job.model.entity.JobRunEntity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;

@Service
@ConditionalOnMissingBean
public class JobDataService {

    private final JobRepository jobRepository;
    private final JobRunRepository jobRunRepository;

    @Value("${job.startup.cleanUpBusyRuns:true}")
    private boolean cleanUpBusyRuns;

    public JobDataService(
            final JobRepository jobRepository, final JobRunRepository jobRunRepository) {
        this.jobRepository = jobRepository;
        this.jobRunRepository = jobRunRepository;
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
    public Optional<String> findLogForJobRun(final Integer jobRunId) {
        return jobRunRepository.findById(jobRunId).map(JobRunEntity::getLogOutput);
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

    public JobRunEntity newRunInstance() {
        return new JobRunEntity();
    }

    public Optional<JobRun> findJobRunById(final int jobRunId) {
        return jobRunRepository.findById(jobRunId);
    }

    public void delete(Job job) {
        jobRepository.delete(JobEntity.fromJob(job));
    }
}
