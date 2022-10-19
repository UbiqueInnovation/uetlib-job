package ch.ubique.backend.job.data.jdbc.params;

import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunInput;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.Instant;
import java.util.Optional;

public class JobParamsCreator {

    private JobParamsCreator() {}

    public static SqlParameterSource createParams(Job job) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        Optional.ofNullable(job.getId()).ifPresent(id -> params.addValue("pk_job_id", id));
        params.addValue("name", job.getName());
        params.addValue("clazz", job.getClazz());
        params.addValue("cron_expression", job.getCronExpression());

        return params;
    }

    public static SqlParameterSource createParams(JobRun jobRun) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        Optional.ofNullable(jobRun.getId())
                .ifPresent(runId -> params.addValue("pk_job_run_id", runId));
        params.addValue("state", jobRun.getState().name());
        params.addValue("fk_job_id", jobRun.getJobId());
        params.addValue("job_version", jobRun.getJobVersion());
        params.addValue(
                "started_at", Optional.ofNullable(jobRun.getStartedAt()).orElse(Instant.now()));
        Optional.ofNullable(jobRun.getFinishedAt())
                .ifPresent(finishedAt -> params.addValue("finished_at", finishedAt));
        params.addValue("log_output", jobRun.getLogOutput());
        return params;
    }

    public static SqlParameterSource createParams(JobRunInput jobRunInput) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        Optional.ofNullable(jobRunInput.getId())
                .ifPresent(runInputId -> params.addValue("pk_job_run_input_id", runInputId));
        params.addValue("fk_job_run_id", jobRunInput.getRunId());
        params.addValue("input_name", jobRunInput.getName());
        params.addValue("input_hash", jobRunInput.getHash());
        return params;
    }
}
