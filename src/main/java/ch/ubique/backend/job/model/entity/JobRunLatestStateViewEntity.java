package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.converters.JobRunArgumentsToStringConverter;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import jakarta.validation.constraints.*;

import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "v_job_run_latest_state")
public class JobRunLatestStateViewEntity {

    @Id
    @Positive
    @Column(name = "pk_job_run_id", nullable = false, insertable = false, updatable = false)
    private Integer id;

    @Positive
    @Column(name = "fk_job_id", nullable = false, insertable = false, updatable = false)
    private Integer jobId;

    @NotBlank
    @Column(name = "version_tag", nullable = false, insertable = false, updatable = false)
    private String versionTag;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(
            name = "latest_state",
            nullable = false,
            insertable = false,
            updatable = false,
            length = Integer.MAX_VALUE)
    private JobRunState latestState;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "runner_key",
            nullable = false,
            insertable = false,
            updatable = false,
            length = Integer.MAX_VALUE)
    private JobRunnerKey runnerKey;

    @PastOrPresent
    @Column(name = "started_at", nullable = false, insertable = false, updatable = false)
    private Instant startedAt;

    @PastOrPresent
    @Column(name = "finished_at", insertable = false, updatable = false)
    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JobRunArgumentsToStringConverter.class)
    @Column(name = "job_run_arguments", insertable = false, updatable = false)
    private JobRunArguments jobRunArguments;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected JobRunLatestStateViewEntity() {}

    public JobRun toJobRun() {
        return new JobRun(
                id,
                jobId,
                versionTag,
                startedAt,
                finishedAt,
                latestState,
                runnerKey,
                jobRunArguments);
    }

    public int id() {
        return id;
    }
}
