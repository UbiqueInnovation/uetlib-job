package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.converters.JobRunArgumentsToStringConverter;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "t_job_run")
public class JobRunEntity {

    @Id
    @Positive
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_run_id", nullable = false)
    private Integer id;

    @NotNull
    @Positive
    @Column(name = "fk_job_id", nullable = false)
    private Integer jobId;

    @NotNull
    @PositiveOrZero
    @Column(name = "job_version", nullable = false)
    private Integer jobVersion;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = Integer.MAX_VALUE)
    private JobRunState state;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "runner_key", nullable = false, length = Integer.MAX_VALUE)
    private JobRunnerKey runnerKey;

    @NotNull
    @PastOrPresent
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @PastOrPresent
    @Column(name = "finished_at")
    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JobRunArgumentsToStringConverter.class)
    @Column(name = "job_run_arguments")
    private JobRunArguments jobRunArguments;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobRunEntity() {}

    public JobRunEntity(final JobRun jobRun) {
        id = jobRun.id();
        jobId = jobRun.jobId();
        jobVersion = jobRun.jobVersion();
        state = jobRun.state();
        runnerKey = jobRun.runnerKey();
        startedAt = jobRun.startedAt();
        finishedAt = jobRun.finishedAt();
        jobRunArguments = jobRun.jobRunArguments();
    }

    public JobRunEntity(
            @Positive final int jobId,
            @PositiveOrZero final int jobVersion,
            @NotNull final JobRunState state,
            @NotNull final JobRunnerKey runnerKey,
            @NotNull @PastOrPresent final Instant startedAt,
            @PastOrPresent final Instant finishedAt,
            final JobRunArguments jobRunArguments) {
        this.jobId = jobId;
        this.jobVersion = jobVersion;
        this.state = state;
        this.runnerKey = runnerKey;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.jobRunArguments = jobRunArguments;
    }

    public JobRun toJobRun() {
        return new JobRun(
                id, jobId, jobVersion, startedAt, finishedAt, state, runnerKey, jobRunArguments);
    }
}
