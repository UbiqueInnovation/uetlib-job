package ch.ubique.backend.job.data.jdbc.service;

import ch.ubique.backend.job.model.jdbc.JdbcJob;
import ch.ubique.backend.job.model.jdbc.JdbcJobRun;
import java.time.Instant;
import java.util.Date;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

public class JobParamsCreator {

    private JobParamsCreator() {}

    public static SqlParameterSource createParamsForJob(JdbcJob job) {
        MapSqlParameterSource parameterSource = new MapSqlParameterSource();
        if (job.getId() != null) {
            parameterSource.addValue("pk_job_id", job.getId());
        }
        parameterSource.addValue("name", job.getName());
        parameterSource.addValue("clazz", job.getClazz());
        parameterSource.addValue("cron_expression", job.getCronExpression());

        return parameterSource;
    }

    public static SqlParameterSource createParamsForJobRun(JdbcJobRun importRun) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (importRun.getId() != null) {
            params.addValue("pk_job_run_id", importRun.getId());
        }
        params.addValue("state", importRun.getState().name());
        params.addValue("fk_job_id", importRun.getJobId());
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
