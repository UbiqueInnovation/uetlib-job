package ch.uetlib.job.model.entity;

import static java.util.Objects.requireNonNull;

import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.converters.JobRunArgumentsToStringConverter;
import ch.uetlib.job.model.converters.StringSetToStringConverter;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.model.runner.conditions.JobRunnerTaint;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import jakarta.validation.constraints.*;

import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "t_job_run_queue")
public class JobQueuedRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_run_queue_id", nullable = false, updatable = false)
    private @Nullable Integer jobQueuedRunId;

    @PositiveOrZero
    @Column(name = "priority")
    private @Nullable Integer priority;

    @Positive
    @Column(name = "fk_job_id", nullable = false, updatable = false)
    private int jobId;

    @NotBlank
    @Column(name = "version_tag", nullable = false)
    private String versionTag;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private @Nullable Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private @Nullable Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "queue_state", nullable = false)
    private JobRunQueueState queueState;

    @Column(name = "fk_job_run_id")
    private @Nullable Integer jobRunId;

    /** The runner application's unique ID */
    @Column(name = "runner")
    private @Nullable String runner;

    @Column(name = "heartbeat_at")
    private @Nullable Instant lastHeartbeat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = StringSetToStringConverter.class)
    @Column(name = "taints", nullable = false)
    private Set<String> taints;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JobRunArgumentsToStringConverter.class)
    @Column(name = "job_run_arguments", nullable = false)
    private JobRunArguments jobRunArguments;

    @SuppressWarnings("NullAway.Init")
    protected JobQueuedRunEntity() {}

    public JobQueuedRunEntity(
            final int jobId,
            final @Nullable Integer priority,
            final String versionTag,
            final JobRunArguments jobRunArguments,
            final Set<JobRunnerTaint> taints) {
        this.jobId = jobId;
        this.priority = priority;
        this.versionTag = versionTag;
        this.queueState = JobRunQueueState.QUEUED;
        this.taints = taints.stream().map(JobRunnerTaint::value).collect(Collectors.toSet());
        this.jobRunArguments = jobRunArguments;
    }

    public JobQueuedRun toJobQueuedRun() {
        return new JobQueuedRun(
                requireNonNull(jobQueuedRunId),
                priority,
                jobId,
                versionTag,
                queueState,
                jobRunId,
                runner,
                lastHeartbeat,
                requireNonNull(createdAt),
                requireNonNull(updatedAt),
                taints.stream().map(JobRunnerTaint::new).collect(Collectors.toSet()),
                jobRunArguments);
    }

    public JobQueuedRunEntity setRunId(final int runId) {
        this.jobRunId = runId;
        this.queueState = JobRunQueueState.RUNNING;
        this.lastHeartbeat = Instant.now();
        return this;
    }
}
