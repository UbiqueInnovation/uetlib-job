package ch.ubique.backend.job.data.jdbc.service;

import ch.ubique.backend.job.data.jdbc.mapper.JobRunInputRowMapper;
import ch.ubique.backend.job.data.jdbc.params.JobParamsCreator;
import ch.ubique.backend.job.data.service.InputJobDataService;
import ch.ubique.backend.job.model.jdbc.JdbcJob;
import ch.ubique.backend.job.model.jdbc.JdbcJobRun;
import ch.ubique.backend.job.model.jdbc.JdbcJobRunInput;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;

import java.util.*;

import javax.sql.DataSource;

@Service
public class JdbcInputJobDataService extends JdbcJobDataService
        implements InputJobDataService<JdbcJob, JdbcJobRun, JdbcJobRunInput> {

    private final SimpleJdbcInsert jobRunInputInsert;

    public JdbcInputJobDataService(DataSource dataSource) {
        super(dataSource);
        jobRunInputInsert =
                new SimpleJdbcInsert(dataSource)
                        .withTableName("t_job_run_input")
                        .usingGeneratedKeyColumns("pk_job_run_input_id");
    }

    @Override
    public void insertJobRunInput(JdbcJobRunInput jobRunInput) {
        jobRunInputInsert.execute(JobParamsCreator.createParams(jobRunInput));
    }

    @Override
    public Collection<JdbcJobRunInput> findJobRunInputs(Integer runId) {
        final var sql = "SELECT *  FROM t_job_run_input WHERE fk_job_run_id = :runId ";
        return jt.query(sql, new MapSqlParameterSource("runId", runId), new JobRunInputRowMapper());
    }
}
