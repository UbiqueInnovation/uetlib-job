package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobClassAndId;

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
@Table(name = "t_job")
public class JobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_id", nullable = false)
    private Integer id;

    @Column(name = "clazz", nullable = false)
    private String canonicalClassName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static JobEntity forClass(final Class<? extends Job> jobClass) {
        final var jobEntity = new JobEntity();
        jobEntity.enabled = true;
        jobEntity.canonicalClassName = jobClass.getCanonicalName();
        return jobEntity;
    }

    public JobClassAndId toRecord() {
        return new JobClassAndId(id, canonicalClassName);
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
