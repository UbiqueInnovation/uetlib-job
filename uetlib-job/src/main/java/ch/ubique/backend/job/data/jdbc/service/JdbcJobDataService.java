package ch.ubique.backend.job.data.jdbc.service;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.jdbc.JdbcJobStatus;
import ch.ubique.backend.job.model.jdbc.JdbcJobRun;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
public class JdbcJobDataService implements JobDataService<JdbcJobStatus, JdbcJobRun> {

    @Override
    public void cleanUpBusyRuns() {
        throw new NotImplementedException(JdbcJobDataService.class + " not yet implemented");
    }

    @Override
    public Optional<String> findLogForJobRun(final Integer jobRunId) {
        throw new NotImplementedException(JdbcJobDataService.class + " not yet implemented");
    }

    @Override
    public Optional<JdbcJobStatus> findJobStatusByClass(final String clazz) {
        throw new NotImplementedException(JdbcJobDataService.class + " not yet implemented");
    }

    @Override
    public JdbcJobStatus updateCronExpression(final String clazz, final String cronExpression) {
        throw new NotImplementedException(JdbcJobDataService.class + " not yet implemented");
    }

    @Override
    public Optional<JdbcJobRun> findLatestJobRun(final Integer jobId) {
        throw new NotImplementedException(JdbcJobDataService.class + " not yet implemented");
    }

    @Override
    public List<JdbcJobStatus> findJobStatuses() {
        throw new NotImplementedException(JdbcJobDataService.class + " not yet implemented");
    }

    @Override
    public Page<JdbcJobRun> findJobRuns(
            final int pageNumber, final int pageSize, final String name, final JobRunState state) {
        throw new NotImplementedException(JdbcJobDataService.class + " not yet implemented");
    }

    @Override
    public JdbcJobStatus registerJobByClass(final Class<?> cls) {
        throw new NotImplementedException(JdbcJobDataService.class + " not yet implemented");
    }

    @Override
    public JdbcJobRun saveJobRun(final JdbcJobRun jobRun) {
        throw new NotImplementedException(JdbcJobDataService.class + " not yet implemented");
    }

    @Override
    public JdbcJobRun newRunInstance() {
        return new JdbcJobRun();
    }
}
