package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobRunDatabaseSubmission;
import ch.ubique.backend.job.model.JobRunDatabaseSubmissionArguments;
import ch.ubique.backend.job.model.converters.JobRunDatabaseSubmissionArgumentsToStringConverter;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

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
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JobRunDatabaseSubmissionArgumentsToStringConverter.class)
    @Column(name = "job_run_database_submission_arguments", nullable = false)
    private JobRunDatabaseSubmissionArguments jobRunDatabaseSubmissionArguments;

    protected JobRunDatabaseSubmissionEntity() {}

    public JobRunDatabaseSubmissionEntity(
            final Integer jobId,
            final Instant submittedAt,
            final JobRunDatabaseSubmissionArguments jobRunDatabaseSubmissionArguments) {
        this.jobId = jobId;
        this.submittedAt = submittedAt;
        this.jobRunDatabaseSubmissionArguments = jobRunDatabaseSubmissionArguments;
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

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(final Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(final Instant executedAt) {
        this.executedAt = executedAt;
    }

    public JobRunDatabaseSubmissionArguments getJobRunDatabaseSubmissionArguments() {
        return jobRunDatabaseSubmissionArguments;
    }

    public void setJobRunDatabaseSubmissionArguments(
            final JobRunDatabaseSubmissionArguments jobRunDatabaseSubmissionArguments) {
        this.jobRunDatabaseSubmissionArguments = jobRunDatabaseSubmissionArguments;
    }

    public JobRunDatabaseSubmission toJobRunDatabaseSubmission() {
        return new JobRunDatabaseSubmission(
                id, jobId, submittedAt, executedAt, jobRunDatabaseSubmissionArguments);
    }
}
