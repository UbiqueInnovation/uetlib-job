package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobRunDatabaseSubmission;
import ch.ubique.backend.job.model.JobRunDatabaseSubmissionArguments;
import ch.ubique.backend.job.model.converters.JobRunDatabaseSubmissionArgumentsToStringConverter;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "t_job_run_database_submission")
public class JobRunDatabaseSubmissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_run_database_submission_id", nullable = false)
    private Integer id;

    @Column(name = "fk_job_id", nullable = false)
    private Integer jobId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JobRunDatabaseSubmissionArgumentsToStringConverter.class)
    @Column(name = "job_run_database_submission_arguments", nullable = false)
    private JobRunDatabaseSubmissionArguments jobRunDatabaseSubmissionArguments;

    protected JobRunDatabaseSubmissionEntity() {}

    public JobRunDatabaseSubmissionEntity(
            final Integer jobId,
            final Instant createdAt,
            final JobRunDatabaseSubmissionArguments jobRunDatabaseSubmissionArguments) {
        this.jobId = jobId;
        this.createdAt = createdAt;
        this.jobRunDatabaseSubmissionArguments =
                Objects.requireNonNull(jobRunDatabaseSubmissionArguments);
    }

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

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
                id, jobId, createdAt, executedAt, jobRunDatabaseSubmissionArguments);
    }
}
