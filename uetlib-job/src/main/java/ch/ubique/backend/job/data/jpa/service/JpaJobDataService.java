package ch.ubique.backend.job.data.jpa.service;

import ch.ubique.backend.core.exception.ResourceNotFoundException;
import ch.ubique.backend.job.data.jpa.repository.JobRepository;
import ch.ubique.backend.job.data.jpa.repository.JobRunRepository;
import ch.ubique.backend.job.data.service.AbstractJobDataService;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.entity.JobEntity;
import ch.ubique.backend.job.model.entity.JobRunEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class JpaJobDataService extends AbstractJobDataService {

    private final JobRepository jobRepository;
    private final JobRunRepository jobRunRepository;

    public JpaJobDataService(
            final JobRepository jobRepository, final JobRunRepository jobRunRepository) {
        this.jobRepository = jobRepository;
        this.jobRunRepository = jobRunRepository;
    }

    @Override
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

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findLogForJobRun(final Integer jobRunId) {
        return jobRunRepository.findById(jobRunId).map(JobRunEntity::getLogOutput);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Job> findJobByClazz(String clazz) {
        return jobRepository.findByClazz(clazz);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Job> findJobById(int id) {
        return jobRepository.findById(id);
    }

    @Override
    @Transactional
    public JobEntity updateCronExpression(final int id, final String cronExpression) {
        return jobRepository
                .findById(id)
                .map(JobEntity.class::cast)
                .map(
                        job -> {
                            job.setDatabaseCronExpression(cronExpression);
                            return jobRepository.save(job);
                        })
                .orElseThrow(
                        ResourceNotFoundException.throwWithMessage(
                                "No JobEntity with id {0} found", id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobRun> findLatestJobRun(final int jobId) {
        return jobRunRepository.findFirstByJobIdOrderByStartedAtDesc(jobId);
    }

    @Override
    public Optional<JobRun> findLatestJobRunByState(int jobId, JobRunState state) {
        return jobRunRepository.findFirstByJobIdAndStateOrderByStartedAtDesc(jobId, state);
    }

    @Override
    public Optional<JobRun> findLatestJobRunByStates(int jobId, JobRunState... states) {
        return jobRunRepository.findFirstByJobIdAndStateInOrderByStartedAtDesc(
                jobId, List.of(states));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Job> findJobs() {
        return jobRepository.findAllByOrderById();
    }

    @Override
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

    @Override
    @Transactional
    public JobEntity registerJobByClass(final Class<?> cls) {
        return findJobByClazz(cls.getName())
                .map(JobEntity.class::cast)
                .orElseGet(
                        () -> {
                            final var job = new JobEntity();
                            job.setName(cls.getSimpleName());
                            job.setClazz(cls.getCanonicalName());
                            job.setDatabaseCronExpression(job.getCronExpression());
                            return jobRepository.save(job);
                        });
    }

    @Override
    @Transactional
    public JobRunEntity saveJobRun(final JobRun jobRun) {
        if (!(jobRun instanceof JobRunEntity jobRunEntity)) {
            throw new ClassCastException(
                    "jobRun cannot be instance of class to JobRunEntity, but is of class"
                            + jobRun.getClass());
        }
        return jobRunRepository.save(jobRunEntity);
    }

    @Override
    public JobRunEntity newRunInstance() {
        return new JobRunEntity();
    }

    @Override
    public Optional<JobRun> findJobRunById(final int jobRunId) {
        return jobRunRepository.findById(jobRunId);
    }

    @Override
    public void delete(Job job) {
        jobRepository.delete(JobEntity.fromJob(job));
    }
}
