package ch.ubique.backend.job.data.jpa.service;

import ch.ubique.backend.core.exception.ResourceNotFoundException;
import ch.ubique.backend.job.data.jpa.repository.JobRepository;
import ch.ubique.backend.job.data.jpa.repository.JobRunRepository;
import ch.ubique.backend.job.data.service.JobDataService;
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
public class JpaJobDataService implements JobDataService {

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
    public Optional<Job> findJobByClass(String clazz) {
        return jobRepository.findByClazz(clazz);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Job> findJobByName(String name) {
        return jobRepository.findByName(name);
    }

    @Override
    @Transactional
    public JobEntity updateCronExpression(final String clazz, final String cronExpression) {
        return jobRepository
                .findByClazz(clazz)
                .map(JobEntity.class::cast)
                .map(
                        job -> {
                            job.setCronExpression(cronExpression);
                            return jobRepository.save(job);
                        })
                .orElseThrow(
                        ResourceNotFoundException.throwWithMessage(
                                "No JobEntity with clazz {0} found", clazz));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobRun> findLatestJobRun(final Integer jobId) {
        return jobRunRepository.findFirstByJobIdOrderByStartedAtDesc(jobId);
    }

    @Override
    public Optional<JobRun> findLatestJobRunByState(Integer jobId, JobRunState state) {
        return jobRunRepository.findFirstByJobIdAndStateOrderByStartedAtDesc(jobId, state);
    }

    @Override
    public Optional<JobRun> findLatestJobRunByStates(Integer jobId, JobRunState... states) {
        return jobRunRepository.findFirstByJobIdAndStateInOrderByStartedAtDesc(
                jobId, List.of(states));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Job> findJobs() {
        return jobRepository.findAllByOrderByName();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JobRun> findJobRuns(
            final int pageNumber, final int pageSize, final String name, final JobRunState state) {
        final var page = PageRequest.of(pageNumber, pageSize);
        if (name != null) {
            final var jobId =
                    jobRepository
                            .findByName(name)
                            .map(Job::getId)
                            .orElseThrow(
                                    ResourceNotFoundException.throwWithMessage(
                                            "No JobEntity with name {0} found", name));
            if (state != null) {
                return jobRunRepository.findAllByJobIdAndStateOrderByStartedAtDesc(
                        jobId, state, page);
            }
            return jobRunRepository.findAllByJobIdOrderByStartedAtDesc(jobId, page);
        }
        if (state != null) {
            return jobRunRepository.findAllByStateOrderByStartedAtDesc(state, page);
        }
        return jobRunRepository.findAllByOrderByStartedAtDesc(page);
    }

    @Override
    @Transactional
    public JobEntity registerJobByClass(final Class<?> cls) {
        return findJobByClass(cls.getName())
                .map(JobEntity.class::cast)
                .orElseGet(
                        () -> {
                            final var job = new JobEntity();
                            job.setName(cls.getSimpleName());
                            job.setClazz(cls.getCanonicalName());
                            job.setCronExpression("");
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
}
