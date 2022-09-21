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
        cleanUpBusyRuns();
    }

    @Transactional
    void cleanUpBusyRuns();

    @Transactional(readOnly = true)
    Optional<String> findLogForJobRun(Integer jobRunId);

    @Transactional(readOnly = true)
    Optional<Job> findJobByClass(String clazz);

    @Transactional(readOnly = true)
    Optional<Job> findJobByName(String name);

    @Transactional
    Job updateCronExpression(String clazz, String cronExpression);

    @Transactional(readOnly = true)
    Optional<JobRun> findLatestJobRun(Integer jobId);

    @Transactional(readOnly = true)
    Optional<JobRun> findLatestJobRunByState(Integer jobId, JobRunState state);

    @Transactional(readOnly = true)
    Optional<JobRun> findLatestJobRunByStates(Integer jobId, JobRunState... states);

    @Transactional(readOnly = true)
    List<Job> findJobs();

    @Transactional(readOnly = true)
    Page<JobRun> findJobRuns(int pageNumber, int pageSize, String name, JobRunState state);

    @Transactional
    Job registerJobByClass(Class<?> cls);

    @Transactional
    JobRun saveJobRun(JobRun jobRun);

    JobRun newRunInstance();
}
