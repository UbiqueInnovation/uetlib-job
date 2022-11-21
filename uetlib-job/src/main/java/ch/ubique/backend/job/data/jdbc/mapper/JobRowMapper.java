package ch.ubique.backend.job.data.jdbc.mapper;

import ch.ubique.backend.job.model.Job;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class JobRowMapper implements RowMapper<Job> {

    @Override
    public Job mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Job(
                rs.getInt("pk_job_id"),
                rs.getString("clazzName"),
                rs.getString("cron_expression")
        );
    }
}
