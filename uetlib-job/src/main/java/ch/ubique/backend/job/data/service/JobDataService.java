package ch.ubique.backend.job.data.service;

import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobState;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

public interface JobDataService<T extends Job, R extends JobRun> {

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
    Optional<T> findJobByClass(String clazz);

    @Transactional
    T updateCronExpression(String clazz, String cronExpression);

    @Transactional(readOnly = true)
    Optional<R> findLatestJobRun(Integer jobId);

    @Transactional(readOnly = true)
    List<T> findJobs();

    @Transactional(readOnly = true)
    Page<R> findJobRuns(int pageNumber, int pageSize, String name, JobState state);

    @Transactional
    T registerJobByClass(Class<?> cls);

    @Transactional
    R saveJobRun(R jobRun);

    R newRunInstance();
}
