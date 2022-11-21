package ch.ubique.backend.job.data.jdbc.service;

import ch.ubique.backend.job.data.jdbc.mapper.JobRowMapper;
import ch.ubique.backend.job.data.jdbc.mapper.JobRunRowMapper;
import ch.ubique.backend.job.data.jdbc.params.JobParamsCreator;
import ch.ubique.backend.job.data.service.AbstractJobDataService;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.jdbc.JdbcJob;
import ch.ubique.backend.job.model.jdbc.JdbcJobRun;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.*;

@Service
public class JdbcJobDataService extends AbstractJobDataService {

    protected final NamedParameterJdbcTemplate jt;

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
        return Optional.ofNullable(jobRunId).map(this::findJobRunById).map(JobRun::getLogOutput);
    }

    private JobRun findJobRunById(Integer jobRunId) {
        if (jobRunId == null) {
            return null;
        }
        try {
            final var sql = "select * from t_job_run where pk_job_run_id = :id";
            return jt.queryForObject(
                    sql, new MapSqlParameterSource("id", jobRunId), new JobRunRowMapper());
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public Optional<Job> findJobByClazz(final String clazz) {
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
    public Optional<Job> findJobById(final int id) {
        try {
            final var sql = "select * from t_job where pk_job_id = :id";
            final var job =
                    jt.queryForObject(sql, new MapSqlParameterSource("id", id), new JobRowMapper());
            return Optional.ofNullable(job);
        } catch (EmptyResultDataAccessException e) {
            logger.info("No job found for id {}.", id);
            return Optional.empty();
        }
    }

    @Override
    public JdbcJob updateCronExpression(final int id, final String cronExpression) {
        if (cronExpression == null) {
            return null;
        }
        var sql = "update t_job set cron_expression = :cronExpression where pk_job_id = :id";
        MapSqlParameterSource params =
                new MapSqlParameterSource(Map.of("cronExpression", cronExpression, "id", id));
        try {
            jt.update(sql, params);
            return findJobById(id).map(JdbcJob.class::cast).orElse(null);
        } catch (DataAccessException e) {
            logger.warn("Could not update cron expression for id {}.", id);
            return null;
        }
    }

    @Override
    public Optional<JobRun> findLatestJobRun(final int jobId) {
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
    public Optional<JobRun> findLatestJobRunByState(final int jobId, final JobRunState state) {
        return findLatestJobRunByStates(jobId, state);
    }

    @Override
    public Optional<JobRun> findLatestJobRunByStates(final int jobId, final JobRunState... states) {
        if (ArrayUtils.isEmpty(states)) {
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
    public List<Job> findJobs() {
        final var sql = "select * from t_job order by pk_job_id";
        try {
            return jt.query(sql, new JobRowMapper()).stream().map(Job.class::cast).toList();
        } catch (EmptyResultDataAccessException e) {
            logger.info("No job statuses found.");
            return Collections.emptyList();
        }
    }

    @Override
    public Page<JobRun> findJobRuns(
            final int page, final int pageSize, final Integer jobId, final JobRunState state) {

        String sql = "select * from t_job_run";

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("page_size", pageSize);
        params.addValue("offset", pageSize * page);

        List<String> andConditions = new ArrayList<>();

        if (state != null) {
            params.addValue("state", state.name());
            andConditions.add("state = :state");
        }
        if (jobId != null) {
            params.addValue("jobId", jobId);
            andConditions.add("fk_job_id = :jobId");
        }
        if (!andConditions.isEmpty()) {
            sql += " where " + String.join(" and ", andConditions);
        }

        sql += " order by started_at desc limit :page_size offset :offset";

        try {
            return new PageImpl<>(jt.query(sql, params, new JobRunRowMapper()))
                    .map(JobRun.class::cast);
        } catch (EmptyResultDataAccessException e) {
            logger.info("No job runs found.");
            return new PageImpl<>(Collections.emptyList());
        }
    }

    @Override
    public JdbcJob registerJobByClass(final Class<?> cls) {
        return findJobByClazz(cls.getName())
                .map(JdbcJob.class::cast)
                .orElseGet(
                        () -> {
                            final var job = new JdbcJob();
                            job.setName(cls.getSimpleName());
                            job.setClazz(cls.getCanonicalName());
                            job.setDatabaseCronExpression(job.getCronExpression());
                            Number pk =
                                    jobInsert.executeAndReturnKey(
                                            JobParamsCreator.createParams(job));
                            job.setId(pk.intValue());
                            return job;
                        });
    }

    @Override
    public JdbcJobRun saveJobRun(final JobRun jobRun) {
        if (!(jobRun instanceof JdbcJobRun jdbcJobRun)) {
            throw new ClassCastException(
                    "jobRun cannot be instance of class to JdbcJobRun, but is of class"
                            + jobRun.getClass());
        }

        if (jdbcJobRun.getId() == null) {
            final int id =
                    jobRunInsert
                            .executeAndReturnKey(JobParamsCreator.createParams(jdbcJobRun))
                            .intValue();
            jdbcJobRun.setId(id);
        } else {
            jt.update(
                    "update t_job_run set finished_at = :finished_at, state = :state, log_output ="
                            + " :log_output where pk_job_run_id = :pk_job_run_id",
                    JobParamsCreator.createParams(jdbcJobRun));
        }
        return jdbcJobRun;
    }

    @Override
    public JdbcJobRun newRunInstance() {
        return new JdbcJobRun();
    }

    @Override
    public Optional<JobRun> findJobRunById(final int runId) {
        try {
            final var sql = "select * from t_job_run where pk_job_run_id = :runId";
            final var jobRun =
                    jt.queryForObject(
                            sql, new MapSqlParameterSource("runId", runId), new JobRunRowMapper());
            return Optional.ofNullable(jobRun);
        } catch (EmptyResultDataAccessException e) {
            logger.info("No job run found for runId {}.", runId);
            return Optional.empty();
        }
    }

    @Override
    public void delete(Job job) {
        jt.update(
                "DELETE FROM t_job WHERE pk_job_id = :pk_job_id",
                JobParamsCreator.createParams(job));
    }
}
