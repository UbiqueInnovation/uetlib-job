package ch.ubique.backend.job.data.jdbc.service;

import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.jdbc.JdbcJobRun;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

public class JobRunRowMapper implements RowMapper<JdbcJobRun> {

    @Override
    public JdbcJobRun mapRow(ResultSet rs, int rowNum) throws SQLException {
        JdbcJobRun jobRun = new JdbcJobRun();
        jobRun.setId(rs.getInt("pk_job_run_id"));
        jobRun.setJobId(rs.getInt("fk_job_id"));
        jobRun.setJobVersion(rs.getInt("job_version"));
        jobRun.setState(JobRunState.valueOf(rs.getString("state")));
        jobRun.setLogOutput(rs.getString("log_output"));
        jobRun.setStartedAt(rs.getTimestamp("started_at").toInstant());
        var finishedAt = rs.getTimestamp("finished_at");
        if (finishedAt != null) {
            jobRun.setFinishedAt(finishedAt.toInstant());
        }
        return jobRun;
    }
}
