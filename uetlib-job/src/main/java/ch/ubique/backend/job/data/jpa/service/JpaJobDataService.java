package ch.ubique.backend.job.data.jpa.service;

import ch.ubique.backend.core.exception.ResourceNotFoundException;
import ch.ubique.backend.job.data.jpa.repository.JobStatusRepository;
import ch.ubique.backend.job.data.jpa.repository.JobRunRepository;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobStatus;
import ch.ubique.backend.job.model.JobState;
import ch.ubique.backend.job.model.entity.JobStatusEntity;
import ch.ubique.backend.job.model.entity.JobRunEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaJobDataService implements JobDataService<JobStatusEntity, JobRunEntity> {

    private final JobStatusRepository jobStatusRepository;
    private final JobRunRepository jobRunRepository;

    public JpaJobDataService(
            final JobStatusRepository jobStatusRepository, final JobRunRepository jobRunRepository) {
        this.jobStatusRepository = jobStatusRepository;
        this.jobRunRepository = jobRunRepository;
    }

    @Override
    @Transactional
    public void cleanUpBusyRuns() {
        final var now = Instant.now();
        jobRunRepository.saveAllAndFlush(
                jobRunRepository.findAllByStateAndFinishedAtIsNull(JobState.BUSY).stream()
                        .map(
                                jobRun -> {
                                    jobRun.setFinishedAt(now);
                                    jobRun.setState(JobState.UNFINISHED);
                                    return jobRunRepository.save(jobRun);
                                })
                        .collect(Collectors.toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findLogForJobRun(final Integer jobRunId) {
        return jobRunRepository.findById(jobRunId).map(JobRunEntity::getLogOutput);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobStatusEntity> findJobStatusByClass(String clazz) {
        return jobStatusRepository.findByClazz(clazz);
    }

    @Override
    @Transactional
    public JobStatusEntity updateCronExpression(final String clazz, final String cronExpression) {
        return jobStatusRepository
                .findByClazz(clazz)
                .map(
                        job -> {
                            job.setCronExpression(cronExpression);
                            return jobStatusRepository.save(job);
                        })
                .orElseThrow(
                        ResourceNotFoundException.throwWithMessage(
                                "No JobEntity with clazz {0} found", clazz));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobRunEntity> findLatestJobRun(final Integer jobId) {
        return jobRunRepository.findFirstByJobIdOrderByStartedAtDesc(jobId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobStatusEntity> findJobStatuses() {
        return jobStatusRepository.findAllByOrderByName();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JobRunEntity> findJobRuns(
            final int pageNumber, final int pageSize, final String name, final JobState state) {
        final var page = PageRequest.of(pageNumber, pageSize);
        if (name != null) {
            final var jobId =
                    jobStatusRepository
                            .findByName(name)
                            .map(JobStatus::getId)
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
    public JobStatusEntity registerJobByClass(final Class<?> cls) {
        return findJobStatusByClass(cls.getName())
                .orElseGet(
                        () -> {
                            final var job = new JobStatusEntity();
                            job.setName(cls.getSimpleName());
                            job.setClazz(cls.getCanonicalName());
                            job.setCronExpression("");
                            return jobStatusRepository.save(job);
                        });
    }

    @Override
    @Transactional
    public JobRunEntity saveJobRun(final JobRunEntity jobRun) {
        return jobRunRepository.save(jobRun);
    }

    @Override
    public JobRunEntity newRunInstance() {
        return new JobRunEntity();
    }
}
