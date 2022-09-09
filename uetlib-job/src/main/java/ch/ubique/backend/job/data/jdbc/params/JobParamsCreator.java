package ch.ubique.backend.job.data.jdbc.params;

import ch.ubique.backend.job.model.JobRunInput;
import ch.ubique.backend.job.model.jdbc.JdbcJob;
import ch.ubique.backend.job.model.jdbc.JdbcJobRun;
import java.time.Instant;
import java.util.Date;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

public class JobParamsCreator {

    private JobParamsCreator() {}

    public static SqlParameterSource createParams(JdbcJob job) {
        MapSqlParameterSource parameterSource = new MapSqlParameterSource();
        if (job.getId() != null) {
            parameterSource.addValue("pk_job_id", job.getId());
        }
        parameterSource.addValue("name", job.getName());
        parameterSource.addValue("clazz", job.getClazz());
        parameterSource.addValue("cron_expression", job.getCronExpression());

        return parameterSource;
    }

    public static SqlParameterSource createParams(JdbcJobRun jobRun) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (jobRun.getId() != null) {
            params.addValue("pk_job_run_id", jobRun.getId());
        }
        params.addValue("state", jobRun.getState().name());
        params.addValue("fk_job_id", jobRun.getJobId());
        params.addValue("job_version", jobRun.getJobVersion());
        if (jobRun.getStartedAt() != null) {
            params.addValue("started_at", Date.from(jobRun.getStartedAt()));
        } else {
            params.addValue("started_at", Date.from(Instant.now()));
        }
        if (jobRun.getFinishedAt() != null) {
            params.addValue("finished_at", Date.from(jobRun.getFinishedAt()));
        }
        params.addValue("log_output", jobRun.getLogOutput());
        return params;
    }

    public static SqlParameterSource createParams(JobRunInput jobRunInput) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (jobRunInput.getId() != null) {
            params.addValue("pk_job_run_input_id", jobRunInput.getId());
        }
        params.addValue("fk_job_run_id", jobRunInput.getRunId());
        params.addValue("input_name", jobRunInput.getName());
        params.addValue("input_hash", jobRunInput.getHash());
        return params;
    }
}
