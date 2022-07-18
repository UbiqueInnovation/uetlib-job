package ch.ubique.backend.job.data.jdbc.service;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.jdbc.JdbcJobRun;
import ch.ubique.backend.job.model.jdbc.JdbcJobStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;

@Service
public class JdbcJobDataService implements JobDataService<JdbcJobStatus, JdbcJobRun> {

    private final NamedParameterJdbcTemplate jt;
    private static final Logger logger = LoggerFactory.getLogger(JdbcJobDataService.class);

    private final SimpleJdbcInsert jobStatusInsert;
    private final SimpleJdbcInsert jobRunInsert;

    public JdbcJobDataService(DataSource dataSource) {
        this.jt = new NamedParameterJdbcTemplate(dataSource);
        this.jobStatusInsert =
                new SimpleJdbcInsert(dataSource)
                        .withTableName("t_job_status")
                        .usingGeneratedKeyColumns("pk_job_status_id");
        this.jobRunInsert =
                new SimpleJdbcInsert(dataSource)
                        .withTableName("t_job_run")
                        .usingGeneratedKeyColumns("pk_job_run_id");
    }

    @Override
    public void cleanUpBusyRuns() {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("busy", JobRunState.BUSY.name());
        params.addValue("unfinished", JobRunState.UNFINISHED.name());
        params.addValue("now", new Date());
        jt.update(
                "update t_job_run set state = :unfinished, finished_at = :now where state = :busy",
                params);
    }

    @Override
    public Optional<String> findLogForJobRun(final Integer jobRunId) {
        if (jobRunId == null) {
            return Optional.empty();
        }
        Optional<JdbcJobRun> jobRun = findJobRunById(jobRunId);
        if (jobRun.isPresent()) {
            return Optional.ofNullable(jobRun.get().getLogOutput());
        } else {
            logger.warn("job run (id={}) not found", jobRunId);
            return Optional.empty();
        }
    }

    private Optional<JdbcJobRun> findJobRunById(Integer jobRunId) {
        if (jobRunId == null) {
            return Optional.empty();
        }
        try {
            final var sql = "select * from t_job_run where pk_job_run_id = :id";
            final var jobRun =
                    jt.queryForObject(
                            sql,
                            new MapSqlParameterSource("id", jobRunId),
                            new JdbcJobRunRowMapper());
            return Optional.ofNullable(jobRun);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<JdbcJobStatus> findJobStatusByClass(final String clazz) {
        if (clazz == null) {
            return Optional.empty();
        }
        try {
            final var sql = "select * from t_job_status where clazz = :clazz";
            final var jobRun =
                    jt.queryForObject(
                            sql,
                            new MapSqlParameterSource("clazz", clazz),
                            new JdbcJobStatusRowMapper());
            return Optional.ofNullable(jobRun);
        } catch (EmptyResultDataAccessException e) {
            logger.info("No job found for class {}.", clazz);
            return Optional.empty();
        }
    }

    @Override
    public JdbcJobStatus updateCronExpression(final String clazz, final String cronExpression) {
        if (clazz == null || cronExpression == null) {
            return null;
        }
        var sql = "update t_job_status set cron_expression = :cronExpression where clazz = :clazz";
        MapSqlParameterSource params =
                new MapSqlParameterSource(
                        Map.of("cron_expression", cronExpression, "clazz", clazz));
        try {
            jt.update(sql, params);
            var jobStatus = findJobStatusByClass(clazz);
            if (jobStatus.isPresent()) {
                return jobStatus.get();
            }
            return null;
        } catch (DataAccessException e) {
            logger.warn("Could not update cron expression for clazz {}.", clazz);
            return null;
        }
    }

    @Override
    public Optional<JdbcJobRun> findLatestJobRun(final Integer jobId) {
        if (jobId == null) {
            return Optional.empty();
        }
        var sql =
                "select * from t_job_run where fk_job_status_id = :jobId order by started_at desc limit 1";
        try {
            return Optional.ofNullable(
                    jt.queryForObject(
                            sql,
                            new MapSqlParameterSource(Map.of("jobId", jobId)),
                            new JdbcJobRunRowMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<JdbcJobRun> findLatestJobRunByState(
            final Integer jobId, final JobRunState state) {
        if (jobId == null || state == null) {
            return Optional.empty();
        }
        var sql =
                "select * from t_job_run where fk_job_status_id = :jobId and state = :state order by started_at desc limit 1";
        try {
            return Optional.ofNullable(
                    jt.queryForObject(
                            sql,
                            new MapSqlParameterSource(
                                    Map.of("fk_job_status_id", jobId, "state", state)),
                            new JdbcJobRunRowMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<JdbcJobStatus> findJobStatuses() {
        final var sql = "select * from t_job_status";
        try {
            return jt.query(sql, new JdbcJobStatusRowMapper());
        } catch (DataAccessException e) {
            logger.info("No job statuses found.");
            return Collections.emptyList();
        }
    }

    @Override
    public Page<JdbcJobRun> findJobRuns(
            final int pageNumber, final int pageSize, final String name, final JobRunState state) {

        String sql = "select * from t_job_run";

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("page_size", pageSize);
        params.addValue("offset", pageSize * pageNumber);

        List<String> andConditions = new ArrayList<>();

        if (state != null) {
            params.addValue("state", state);
            andConditions.add("state = :state");
        }
        if (name != null) {
            params.addValue("name", name);
            andConditions.add(
                    "fk_job_status_id = (select pk_job_status_id from t_job_status where name = :name)");
        }
        if (!andConditions.isEmpty()) {
            sql += " where " + String.join(" and ", andConditions);
        }

        sql += " order by started_at desc limit :page_size offset :offset";

        try {
            return new PageImpl<>(jt.query(sql, params, new JdbcJobRunRowMapper()));
        } catch (DataAccessException e) {
            logger.info("No job runs found.");
            return new PageImpl<>(Collections.emptyList());
        }
    }

    @Override
    public JdbcJobStatus registerJobByClass(final Class<?> cls) {
        final var existingJobStatus = findJobStatusByClass(cls.getName());
        if (existingJobStatus.isPresent()) {
            return existingJobStatus.get();
        }
        final var jobStatus = new JdbcJobStatus();
        jobStatus.setName(cls.getSimpleName());
        jobStatus.setClazz(cls.getCanonicalName());
        jobStatus.setCronExpression("not set");
        Number pk =
                jobStatusInsert.executeAndReturnKey(
                        JdbcJobParamsCreator.createParamsForJdbcJobStatus(jobStatus));
        jobStatus.setId(pk.intValue());
        return jobStatus;
    }

    @Override
    public JdbcJobRun saveJobRun(final JdbcJobRun jobRun) {
        if (jobRun.getId() == null) {
            final int id =
                    jobRunInsert
                            .executeAndReturnKey(JdbcJobParamsCreator.createParamsForJobRun(jobRun))
                            .intValue();
            jobRun.setId(id);
        } else {
            jt.update(
                    "update t_job_run set finished_at = :finished_at, state = :state, log_output = :log_output where pk_job_run_id = :pk_job_run_id",
                    JdbcJobParamsCreator.createParamsForJobRun(jobRun));
        }
        return jobRun;
    }

    @Override
    public JdbcJobRun newRunInstance() {
        return new JdbcJobRun();
    }
}
