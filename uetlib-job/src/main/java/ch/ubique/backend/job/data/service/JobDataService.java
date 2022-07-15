package ch.ubique.backend.job.data.service;

import ch.ubique.backend.job.model.JobStatus;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

public interface JobDataService<S extends JobStatus, R extends JobRun> {

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
    Optional<S> findJobStatusByClass(String clazz);

    @Transactional
    S updateCronExpression(String clazz, String cronExpression);

    @Transactional(readOnly = true)
    Optional<R> findLatestJobRun(Integer jobId);

    @Transactional(readOnly = true)
    Optional<R> findLatestJobRunByState(Integer jobId, JobRunState state);

    @Transactional(readOnly = true)
    List<S> findJobStatuses();

    @Transactional(readOnly = true)
    Page<R> findJobRuns(int pageNumber, int pageSize, String name, JobRunState state);

    @Transactional
    S registerJobByClass(Class<?> cls);

    @Transactional
    R saveJobRun(R jobRun);

    R newRunInstance();
}
