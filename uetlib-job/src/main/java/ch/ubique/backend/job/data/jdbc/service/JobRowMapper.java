package ch.ubique.backend.job.data.jdbc.service;

import ch.ubique.backend.job.model.jdbc.JdbcJob;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

public class JobRowMapper implements RowMapper<JdbcJob> {

    @Override
    public JdbcJob mapRow(ResultSet rs, int rowNum) throws SQLException {
        JdbcJob job = new JdbcJob();
        job.setId(rs.getInt("pk_job_id"));
        job.setName(rs.getString("name"));
        job.setClazz(rs.getString("clazz"));
        job.setCronExpression(rs.getString("cron_expression"));
        return job;
    }
}
