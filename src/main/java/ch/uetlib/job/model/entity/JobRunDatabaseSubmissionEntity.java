package ch.uetlib.job.model.entity;

import static java.util.Objects.requireNonNull;

import ch.uetlib.job.model.JobRunDatabaseSubmission;
import ch.uetlib.job.model.JobRunDatabaseSubmissionArguments;
import ch.uetlib.job.model.converters.JobRunDatabaseSubmissionArgumentsToStringConverter;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Entity
@Table(name = "t_job_run_database_submission")
public class JobRunDatabaseSubmissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_run_database_submission_id", nullable = false)
    private @Nullable Integer id;

    @Column(name = "fk_job_id", nullable = false)
    private @Nullable Integer jobId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @Column(name = "executed_at")
    private @Nullable Instant executedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JobRunDatabaseSubmissionArgumentsToStringConverter.class)
    @Column(name = "job_run_database_submission_arguments", nullable = false)
    private @Nullable JobRunDatabaseSubmissionArguments jobRunDatabaseSubmissionArguments;

    protected JobRunDatabaseSubmissionEntity() {}

    public JobRunDatabaseSubmissionEntity(
            final Integer jobId,
            final Instant createdAt,
            final JobRunDatabaseSubmissionArguments jobRunDatabaseSubmissionArguments) {
        this.jobId = jobId;
        this.createdAt = createdAt;
        this.jobRunDatabaseSubmissionArguments = requireNonNull(jobRunDatabaseSubmissionArguments);
    }

    @Nullable
    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    @Nullable
    public Integer getJobId() {
        return jobId;
    }

    public void setJobId(final Integer jobId) {
        this.jobId = jobId;
    }

    public void setExecutedAt(final Instant executedAt) {
        this.executedAt = executedAt;
    }

    @Nullable
    public JobRunDatabaseSubmission toJobRunDatabaseSubmission() {
        if (jobRunDatabaseSubmissionArguments == null) {
            return null;
        }
        return new JobRunDatabaseSubmission(
                requireNonNull(id),
                requireNonNull(jobId),
                requireNonNull(createdAt),
                executedAt,
                jobRunDatabaseSubmissionArguments);
    }
}
