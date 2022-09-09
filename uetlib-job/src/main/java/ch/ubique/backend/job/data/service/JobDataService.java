package ch.ubique.backend.job.data.service;

import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

public interface JobDataService<J extends Job, R extends JobRun> {

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
    Optional<J> findJobByClass(String clazz);

    @Transactional(readOnly = true)
    Optional<J> findJobByName(String name);

    @Transactional
    J updateCronExpression(String clazz, String cronExpression);

    @Transactional(readOnly = true)
    Optional<R> findLatestJobRun(Integer jobId);

    @Transactional(readOnly = true)
    Optional<R> findLatestJobRunByState(Integer jobId, JobRunState state);

    @Transactional(readOnly = true)
    Optional<R> findLatestJobRunByStates(Integer jobId, JobRunState... states);

    @Transactional(readOnly = true)
    List<J> findJobs();

    @Transactional(readOnly = true)
    Page<R> findJobRuns(int pageNumber, int pageSize, String name, JobRunState state);

    @Transactional
    J registerJobByClass(Class<?> cls);

    @Transactional
    R saveJobRun(R jobRun);

    R newRunInstance();
}
