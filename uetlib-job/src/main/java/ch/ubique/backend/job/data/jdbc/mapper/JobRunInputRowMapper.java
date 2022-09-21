package ch.ubique.backend.job.data.jdbc.mapper;

import ch.ubique.backend.job.model.jdbc.JdbcJobRunInput;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class JobRunInputRowMapper implements RowMapper<JdbcJobRunInput> {

    @Override
    public JdbcJobRunInput mapRow(ResultSet rs, int rowNum) throws SQLException {
        final var jobRunInput = new JdbcJobRunInput();
        jobRunInput.setId(rs.getInt("pk_job_run_input_id"));
        jobRunInput.setRunId(rs.getInt("fk_job_run_id"));
        jobRunInput.setName(rs.getString("input_name"));
        jobRunInput.setHash(rs.getString("input_hash"));
        return jobRunInput;
    }
}
