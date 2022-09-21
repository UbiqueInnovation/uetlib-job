package ch.ubique.backend.job.data.jdbc.service;

import ch.ubique.backend.job.data.jdbc.mapper.JobRowMapper;
import ch.ubique.backend.job.data.jdbc.mapper.JobRunRowMapper;
import ch.ubique.backend.job.data.jdbc.params.JobParamsCreator;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.jdbc.JdbcJob;
import ch.ubique.backend.job.model.jdbc.JdbcJobRun;

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

import java.util.*;

import javax.sql.DataSource;

@Service
public class JdbcJobDataService implements JobDataService<JdbcJob, JdbcJobRun> {

    protected final NamedParameterJdbcTemplate jt;
    private static final Logger logger = LoggerFactory.getLogger(JdbcJobDataService.class);

    private final SimpleJdbcInsert jobInsert;
    private final SimpleJdbcInsert jobRunInsert;

    public JdbcJobDataService(DataSource dataSource) {
        jt = new NamedParameterJdbcTemplate(dataSource);
        jobInsert =
                new SimpleJdbcInsert(dataSource)
                        .withTableName("t_job")
                        .usingGeneratedKeyColumns("pk_job_id");
        jobRunInsert =
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
                            sql, new MapSqlParameterSource("id", jobRunId), new JobRunRowMapper());
            return Optional.ofNullable(jobRun);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<JdbcJob> findJobByClass(final String clazz) {
        if (clazz == null) {
            return Optional.empty();
        }
        try {
            final var sql = "select * from t_job where clazz = :clazz";
            final var jobRun =
                    jt.queryForObject(
                            sql, new MapSqlParameterSource("clazz", clazz), new JobRowMapper());
            return Optional.ofNullable(jobRun);
        } catch (EmptyResultDataAccessException e) {
            logger.info("No job found for class {}.", clazz);
            return Optional.empty();
        }
    }

    @Override
    public Optional<JdbcJob> findJobByName(final String name) {
        if (name == null) {
            return Optional.empty();
        }
        try {
            final var sql = "select * from t_job where name = :name";
            final var jobRun =
                    jt.queryForObject(
                            sql, new MapSqlParameterSource("name", name), new JobRowMapper());
            return Optional.ofNullable(jobRun);
        } catch (EmptyResultDataAccessException e) {
            logger.info("No job found for name {}.", name);
            return Optional.empty();
        }
    }

    @Override
    public JdbcJob updateCronExpression(final String clazz, final String cronExpression) {
        if (clazz == null || cronExpression == null) {
            return null;
        }
        var sql = "update t_job set cron_expression = :cron_expression where clazz = :clazz";
        MapSqlParameterSource params =
                new MapSqlParameterSource(
                        Map.of("cron_expression", cronExpression, "clazz", clazz));
        try {
            jt.update(sql, params);
            var job = findJobByClass(clazz);
            if (job.isPresent()) {
                return job.get();
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
                "select * from t_job_run where fk_job_id = :jobId order by started_at desc limit 1";
        try {
            return Optional.ofNullable(
                    jt.queryForObject(
                            sql,
                            new MapSqlParameterSource(Map.of("jobId", jobId)),
                            new JobRunRowMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<JdbcJobRun> findLatestJobRunByState(
            final Integer jobId, final JobRunState state) {
        return findLatestJobRunByStates(jobId, state);
    }

    @Override
    public Optional<JdbcJobRun> findLatestJobRunByStates(
            final Integer jobId, final JobRunState... states) {
        if (jobId == null || states == null || states.length == 0) {
            return Optional.empty();
        }
        var sql =
                "select * from t_job_run where fk_job_id = :jobId and state in (:states) order by"
                    + " started_at desc limit 1";
        try {
            return Optional.ofNullable(
                    jt.queryForObject(
                            sql,
                            new MapSqlParameterSource(
                                    Map.of(
                                            "jobId",
                                            jobId,
                                            "states",
                                            Arrays.stream(states).map(Enum::name).toList())),
                            new JobRunRowMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<JdbcJob> findJobs() {
        final var sql = "select * from t_job";
        try {
            return jt.query(sql, new JobRowMapper());
        } catch (EmptyResultDataAccessException e) {
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
            params.addValue("state", state.name());
            andConditions.add("state = :state");
        }
        if (name != null) {
            params.addValue("name", name);
            andConditions.add("fk_job_id = (select pk_job_id from t_job where name = :name)");
        }
        if (!andConditions.isEmpty()) {
            sql += " where " + String.join(" and ", andConditions);
        }

        sql += " order by started_at desc limit :page_size offset :offset";

        try {
            return new PageImpl<>(jt.query(sql, params, new JobRunRowMapper()));
        } catch (EmptyResultDataAccessException e) {
            logger.info("No job runs found.");
            return new PageImpl<>(Collections.emptyList());
        }
    }

    @Override
    public JdbcJob registerJobByClass(final Class<?> cls) {
        final var existingJob = findJobByClass(cls.getName());
        if (existingJob.isPresent()) {
            return existingJob.get();
        }
        final var job = new JdbcJob();
        job.setName(cls.getSimpleName());
        job.setClazz(cls.getCanonicalName());
        job.setCronExpression("not set");
        Number pk = jobInsert.executeAndReturnKey(JobParamsCreator.createParams(job));
        job.setId(pk.intValue());
        return job;
    }

    @Override
    public JdbcJobRun saveJobRun(final JdbcJobRun jobRun) {
        if (jobRun.getId() == null) {
            final int id =
                    jobRunInsert
                            .executeAndReturnKey(JobParamsCreator.createParams(jobRun))
                            .intValue();
            jobRun.setId(id);
        } else {
            jt.update(
                    "update t_job_run set finished_at = :finished_at, state = :state, log_output ="
                        + " :log_output where pk_job_run_id = :pk_job_run_id",
                    JobParamsCreator.createParams(jobRun));
        }
        return jobRun;
    }

    @Override
    public JdbcJobRun newRunInstance() {
        return new JdbcJobRun();
    }
}
