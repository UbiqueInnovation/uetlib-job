package ch.ubique.backend.job.data.jdbc.service;

import ch.ubique.backend.job.model.jdbc.JdbcJobStatus;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class JdbcJobStatusRowMapper implements RowMapper<JdbcJobStatus> {

    @Override
    public JdbcJobStatus mapRow(ResultSet rs, int rowNum) throws SQLException {
        JdbcJobStatus jobStatus = new JdbcJobStatus();
        jobStatus.setId(rs.getInt("pk_job_status_id"));
        jobStatus.setName(rs.getString("name"));
        jobStatus.setClazz(rs.getString("clazz"));
        jobStatus.setCronExpression(rs.getString("cron_expression"));
        return jobStatus;
    }
}
