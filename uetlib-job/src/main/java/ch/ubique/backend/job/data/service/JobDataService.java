package ch.ubique.backend.job.data.service;

import ch.ubique.backend.core.exception.ResourceNotFoundException;
import ch.ubique.backend.job.data.repository.JobRepository;
import ch.ubique.backend.job.data.repository.JobRunRepository;
import ch.ubique.backend.job.model.JobState;
import ch.ubique.backend.job.model.entity.JobEntity;
import ch.ubique.backend.job.model.entity.JobRunEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobDataService {

    private final JobRepository jobRepository;
    private final JobRunRepository jobRunRepository;

    public JobDataService(
            final JobRepository jobRepository, final JobRunRepository jobRunRepository) {
        this.jobRepository = jobRepository;
        this.jobRunRepository = jobRunRepository;
    }

    @PostConstruct
    @Transactional
    public void init() {
        cleanUpBusyRuns();
    }

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

    @Transactional(readOnly = true)
    public Optional<String> findLogForJobRun(final Integer jobRunId) {
        return jobRunRepository.findById(jobRunId).map(JobRunEntity::getLogOutput);
    }

    @Transactional(readOnly = true)
    public Optional<JobEntity> findJobByClass(String clazz) {
        return jobRepository.findByClazz(clazz);
    }

    @Transactional
    public JobEntity updateCronExpression(final String clazz, final String cronExpression) {
        return jobRepository
                .findByClazz(clazz)
                .map(
                        job -> {
                            job.setCronExpression(cronExpression);
                            return jobRepository.save(job);
                        })
                .orElseThrow(
                        ResourceNotFoundException.throwWithMessage(
                                "No JobEntity with clazz {0} found", clazz));
    }

    @Transactional(readOnly = true)
    public Optional<JobRunEntity> findLatestJobRun(final Integer jobId) {
        return jobRunRepository.findFirstByJobIdOrderByStartedAtDesc(jobId);
    }

    @Transactional(readOnly = true)
    public List<JobEntity> findJobs() {
        return jobRepository.findAllByOrderByName();
    }

    @Transactional(readOnly = true)
    public Page<JobRunEntity> findJobRuns(
            final int pageNumber, final int pageSize, final String name, final JobState state) {
        final var page = PageRequest.of(pageNumber, pageSize);
        if (name != null) {
            final var jobId =
                    jobRepository
                            .findByName(name)
                            .map(JobEntity::getId)
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

    @Transactional
    public JobEntity registerJobByClass(final Class<?> cls) {
        return findJobByClass(cls.getName())
                .orElseGet(
                        () -> {
                            final var job = new JobEntity();
                            job.setName(cls.getSimpleName());
                            job.setClazz(cls.getCanonicalName());
                            job.setCronExpression("");
                            return jobRepository.save(job);
                        });
    }

    @Transactional
    public JobRunEntity saveJobRun(final JobRunEntity jobRun) {
        return jobRunRepository.save(jobRun);
    }
}
