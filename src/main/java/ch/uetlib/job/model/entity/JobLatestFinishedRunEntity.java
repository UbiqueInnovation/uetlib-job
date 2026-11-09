package ch.uetlib.job.model.entity;

import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.converters.JobRunArgumentsToStringConverter;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.triggers.TriggerType;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import jakarta.validation.constraints.*;

import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

@Entity
@Immutable
@Table(name = "v_latest_finished_job_run")
public class JobLatestFinishedRunEntity {

    @Id
    @Positive
    @Column(name = "pk_job_run_id", nullable = false)
    private @Nullable Integer id;

    @Positive
    @Column(name = "fk_job_id", nullable = false)
    private @Nullable Integer jobId;

    @NotBlank
    @Column(name = "version_tag", nullable = false)
    private @Nullable String versionTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "runner_key", nullable = false)
    private @Nullable JobRunnerKey runnerKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type")
    private @Nullable TriggerType triggerType;

    @Column(name = "started_at", nullable = false)
    private @Nullable Instant startedAt;

    @Column(name = "finished_at")
    private @Nullable Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JobRunArgumentsToStringConverter.class)
    @Column(name = "job_run_arguments")
    private @Nullable JobRunArguments jobRunArguments;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "latest_state", nullable = false)
    private @Nullable JobRunState latestState;

    public JobRun toJobRun() {
        return new JobRun(
                Objects.requireNonNull(id),
                Objects.requireNonNull(jobId),
                Objects.requireNonNull(versionTag),
                Objects.requireNonNull(startedAt),
                finishedAt,
                Objects.requireNonNull(latestState),
                Objects.requireNonNull(runnerKey),
                triggerType,
                jobRunArguments);
    }
}
