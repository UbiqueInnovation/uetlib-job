package ch.ubique.backend.job.data.jdbc.service;

import ch.ubique.backend.job.model.jdbc.JdbcJobRun;
import ch.ubique.backend.job.model.jdbc.JdbcJobStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.Instant;
import java.util.Date;

public class JdbcJobParamsCreator {

    private JdbcJobParamsCreator() {}

    public static SqlParameterSource createParamsForJdbcJobStatus(JdbcJobStatus jobStatus) {
        MapSqlParameterSource parameterSource = new MapSqlParameterSource();
        if (jobStatus.getId() != null) {
            parameterSource.addValue("pk_job_status_id", jobStatus.getId());
        }
        parameterSource.addValue("name", jobStatus.getName());
        parameterSource.addValue("clazz", jobStatus.getClazz());
        parameterSource.addValue("cron_expression", jobStatus.getCronExpression());

        return parameterSource;
    }

    public static SqlParameterSource createParamsForJobRun(JdbcJobRun importRun) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (importRun.getId() != null) {
            params.addValue("pk_job_run_id", importRun.getId());
        }
        params.addValue("state", importRun.getState().name());
        params.addValue("fk_job_status_id", importRun.getJobStatusId());
        if (importRun.getStartedAt() != null) {
            params.addValue("started_at", Date.from(importRun.getStartedAt()));
        } else {
            params.addValue("started_at", Date.from(Instant.now()));
        }
        if (importRun.getFinishedAt() != null) {
            params.addValue("finished_at", Date.from(importRun.getFinishedAt()));
        }
        params.addValue("log_output", importRun.getLogOutput());
        return params;
    }
}
