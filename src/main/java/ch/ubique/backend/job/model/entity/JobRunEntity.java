package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.converters.JobRunArgumentsToStringConverter;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;

import jakarta.persistence.*;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.*;

import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "t_job_run")
public class JobRunEntity {

    @NotEmpty
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "t_job_run_state_transition",
            joinColumns = @JoinColumn(name = "fk_job_run_id"))
    @OrderBy("transitionedAt desc")
    private final List<JobRunStateTransitionEmbeddable> jobRunStateTransitions = new ArrayList<>();

    @Id
    @Positive
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_run_id", nullable = false)
    private Integer id;

    @Positive
    @Column(name = "fk_job_id", nullable = false)
    private Integer jobId;

    @NotBlank
    @Column(name = "version_tag", nullable = false)
    private String versionTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "runner_key", nullable = false, length = Integer.MAX_VALUE)
    private JobRunnerKey runnerKey;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JobRunArgumentsToStringConverter.class)
    @Column(name = "job_run_arguments")
    private JobRunArguments jobRunArguments;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobRunEntity() {}

    public JobRunEntity(
            @Positive final int jobId,
            @NotBlank final String versionTag,
            @NotNull final JobRunnerKey runnerKey,
            @NotNull @PastOrPresent final Instant startedAt,
            final JobRunArguments jobRunArguments) {
        this.jobId = jobId;
        this.versionTag = versionTag;
        this.runnerKey = runnerKey;
        this.startedAt = startedAt;
        this.jobRunArguments = jobRunArguments;
    }

    public JobRunState latestState() {
        return jobRunStateTransitions.stream()
                .findFirst()
                .map(JobRunStateTransitionEmbeddable::state)
                .orElse(JobRunState.NONE);
    }

    public JobRun toJobRun() {
        return new JobRun(
                id,
                jobId,
                versionTag,
                startedAt,
                finishedAt,
                latestState(),
                runnerKey,
                jobRunArguments);
    }

    public void setRunnerKey(final JobRunnerKey runnerKey) {
        this.runnerKey = runnerKey;
    }

    public void setJobRunArguments(final JobRunArguments jobRunArguments) {
        this.jobRunArguments = jobRunArguments;
    }

    public void setFinishedAt(final Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public void setStartedAt(final Instant startedAt) {
        this.startedAt = startedAt;
    }

    public void addJobRunStateTransition(
            final JobRunStateTransitionEmbeddable jobRunStateEmbeddableTransition) {
        jobRunStateTransitions.add(jobRunStateEmbeddableTransition);
    }

    public @PastOrPresent Instant finishedAt() {
        return finishedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }
}
