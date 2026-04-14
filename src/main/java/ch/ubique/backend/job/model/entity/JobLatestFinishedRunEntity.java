package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.converters.JobRunArgumentsToStringConverter;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.triggers.TriggerType;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import jakarta.validation.constraints.*;

import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Immutable
@Table(name = "v_latest_finished_job_run")
public class JobLatestFinishedRunEntity {

    @Id
    @Positive
    @Column(name = "pk_job_run_id", nullable = false)
    private Integer id;

    @Positive
    @Column(name = "fk_job_id", nullable = false)
    private Integer jobId;

    @NotBlank
    @Column(name = "version_tag", nullable = false)
    private String versionTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "runner_key", nullable = false)
    private JobRunnerKey runnerKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type")
    private TriggerType triggerType;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

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

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "latest_state", nullable = false)
    private JobRunState latestState;

    public JobRun toJobRun() {
        return new JobRun(
                id,
                jobId,
                versionTag,
                startedAt,
                finishedAt,
                latestState,
                runnerKey,
                triggerType,
                jobRunArguments);
    }
}
