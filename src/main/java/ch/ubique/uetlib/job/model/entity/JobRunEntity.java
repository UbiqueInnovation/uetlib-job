package ch.ubique.uetlib.job.model.entity;

import static java.util.Objects.requireNonNull;

import ch.ubique.uetlib.job.model.JobRun;
import ch.ubique.uetlib.job.model.JobRunArguments;
import ch.ubique.uetlib.job.model.JobRunState;
import ch.ubique.uetlib.job.model.converters.JobRunArgumentsToStringConverter;
import ch.ubique.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.ubique.uetlib.job.model.triggers.TriggerType;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import jakarta.validation.constraints.*;
import java.time.Instant;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "t_job_run")
public class JobRunEntity {

    @Id
    @Positive
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_run_id", nullable = false)
    private @Nullable Integer id;

    @Positive
    @Column(name = "fk_job_id", nullable = false)
    private @Nullable Integer jobId;

    @NotBlank
    @Column(name = "version_tag", nullable = false)
    private @Nullable String versionTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "runner_key", nullable = false, length = Integer.MAX_VALUE)
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
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "latest_state", nullable = false)
    private @Nullable JobRunState latestState;

    protected JobRunEntity() {}

    public JobRunEntity(
            final int jobId,
            final String versionTag,
            final JobRunnerKey runnerKey,
            final Instant startedAt,
            final JobRunArguments jobRunArguments) {
        this.jobId = jobId;
        this.versionTag = versionTag;
        this.runnerKey = runnerKey;
        this.startedAt = startedAt;
        this.triggerType = jobRunArguments.triggerType();
        this.jobRunArguments = jobRunArguments;
    }

    @Nullable
    public JobRunState latestState() {
        return latestState;
    }

    public JobRun toJobRun() {
        return new JobRun(
                requireNonNull(id),
                requireNonNull(jobId),
                requireNonNull(versionTag),
                requireNonNull(startedAt),
                finishedAt,
                requireNonNull(latestState),
                requireNonNull(runnerKey),
                triggerType,
                jobRunArguments);
    }

    public void setRunnerKey(final JobRunnerKey runnerKey) {
        this.runnerKey = runnerKey;
    }

    public void setJobRunArguments(final @Nullable JobRunArguments jobRunArguments) {
        this.jobRunArguments = jobRunArguments;
    }

    public void setFinishedAt(final @Nullable Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public void setStartedAt(final Instant startedAt) {
        this.startedAt = startedAt;
    }

    public void setLatestState(final JobRunState latestState) {
        this.latestState = latestState;
    }

    @Nullable
    public Instant finishedAt() {
        return finishedAt;
    }

    @Nullable
    public Integer id() {
        return id;
    }
}
