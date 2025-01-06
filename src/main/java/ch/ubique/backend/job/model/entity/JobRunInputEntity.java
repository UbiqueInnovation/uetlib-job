package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.core.file.ByteFile;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunInput;

import ch.ubique.backend.job.model.input.JobRunInputDeclaration;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "t_job_run_input")
public class JobRunInputEntity implements JobRunInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_run_input_id", nullable = false)
    private Integer id;

    @Column(name = "fk_job_run_id", nullable = false)
    private Integer runId;

    @Column(name = "input_name", nullable = false)
    private String name;

    @Column(name = "input_hash", nullable = false, length = 128)
    private String hash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static JobRunInputEntity from(final JobRun jobRun, final ByteFile file) {
        final var input = new JobRunInputEntity();
        input.setRunId(jobRun.id());
        input.setName(file.getFileName());
        input.setHash(file.getFileHash());
        return input;
    }

    public static JobRunInputEntity from(final JobRunInputDeclaration declaration) {
        final var input = new JobRunInputEntity();
        input.setRunId(declaration.jobRunId());
        input.setName(declaration.path());
        input.setHash(declaration.hash());
        return input;
    }

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(final Integer id) {
        this.id = id;
    }

    @Override
    public Integer getRunId() {
        return runId;
    }

    @Override
    public void setRunId(final Integer runId) {
        this.runId = runId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public String getHash() {
        return hash;
    }

    @Override
    public void setHash(final String hash) {
        this.hash = hash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
