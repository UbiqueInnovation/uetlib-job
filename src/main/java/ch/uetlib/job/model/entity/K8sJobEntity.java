package ch.uetlib.job.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Entity
@Table(name = "t_k8s_jobs")
public class K8sJobEntity {

    @Id
    @Positive
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_k8s_jobs_id", nullable = false)
    private @Nullable Integer id;

    @NotNull
    @Positive
    @Column(name = "fk_job_run_id", nullable = false)
    private @Nullable Integer jobRunId;

    @NotBlank
    @Column(name = "k8s_job_name", nullable = false)
    private String k8sJobName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    protected K8sJobEntity() {}

    public K8sJobEntity(final int jobRunId, final String k8sJobName) {
        this.jobRunId = jobRunId;
        this.k8sJobName = k8sJobName;
    }

    public String k8sJobName() {
        return k8sJobName;
    }

    @Nullable
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Nullable
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
