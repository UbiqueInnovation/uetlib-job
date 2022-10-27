package ch.ubique.backend.job.data.service;

import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;

import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;

public interface JobDataService {

    @PostConstruct
    @Transactional
    default void init() {
        if (doCleanUpBusyRuns()) {
            cleanUpBusyRuns();
        }
    }

    boolean doCleanUpBusyRuns();

    @Transactional
    void cleanUpBusyRuns();

    @Transactional(readOnly = true)
    Optional<String> findLogForJobRun(Integer jobRunId);

    @Transactional(readOnly = true)
    Optional<Job> findJobByClazz(String clazz);

    @Transactional(readOnly = true)
    Optional<Job> findJobById(int id);

    @Transactional
    Job updateCronExpression(int id, String cronExpression);

    @Transactional(readOnly = true)
    Optional<JobRun> findLatestJobRun(int jobId);

    @Transactional(readOnly = true)
    Optional<JobRun> findLatestJobRunByState(int jobId, JobRunState state);

    @Transactional(readOnly = true)
    Optional<JobRun> findLatestJobRunByStates(int jobId, JobRunState... states);

    @Transactional(readOnly = true)
    List<Job> findJobs();

    @Transactional(readOnly = true)
    Page<JobRun> findJobRuns(int page, int pageSize, Integer jobId, JobRunState state);

    @Transactional
    Job registerJobByClass(Class<?> cls);

    @Transactional
    JobRun saveJobRun(JobRun jobRun);

    @Transactional
    JobRun newRunInstance();

    @Transactional(readOnly = true)
    Optional<JobRun> findJobRunById(int jobRunId);

    @Transactional
    void delete(Job job);
}
