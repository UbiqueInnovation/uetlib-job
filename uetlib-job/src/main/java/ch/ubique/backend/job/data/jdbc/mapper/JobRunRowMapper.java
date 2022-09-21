package ch.ubique.backend.job.data.jdbc.mapper;

import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.jdbc.JdbcJobRun;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

public class JobRunRowMapper implements RowMapper<JobRun> {

    @Override
    public JobRun mapRow(ResultSet rs, int rowNum) throws SQLException {
        JdbcJobRun jobRun = new JdbcJobRun();
        jobRun.setId(rs.getInt("pk_job_run_id"));
        jobRun.setJobId(rs.getInt("fk_job_id"));
        jobRun.setJobVersion(rs.getInt("job_version"));
        jobRun.setState(JobRunState.valueOf(rs.getString("state")));
        jobRun.setLogOutput(rs.getString("log_output"));
        jobRun.setStartedAt(rs.getTimestamp("started_at").toInstant());
        Optional.ofNullable(rs.getTimestamp("finished_at"))
                .map(Timestamp::toInstant)
                .ifPresent(jobRun::setFinishedAt);
        return jobRun;
    }
}
