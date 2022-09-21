package ch.ubique.backend.job.data.jdbc.mapper;

import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.jdbc.JdbcJob;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class JobRowMapper implements RowMapper<Job> {

    @Override
    public Job mapRow(ResultSet rs, int rowNum) throws SQLException {
        JdbcJob job = new JdbcJob();
        job.setId(rs.getInt("pk_job_id"));
        job.setName(rs.getString("name"));
        job.setClazz(rs.getString("clazz"));
        job.setCronExpression(rs.getString("cron_expression"));
        return job;
    }
}
