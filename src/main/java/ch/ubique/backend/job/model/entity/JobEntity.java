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
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "t_job")
public class JobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_id", nullable = false)
    private @Nullable Integer id;

    @Column(name = "clazz", nullable = false)
    private @Nullable String canonicalClassName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "priority", nullable = false)
    private int priority;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    public static JobEntity newForClass(final Class<? extends Job> jobClass) {
        final var jobEntity = new JobEntity();
        jobEntity.enabled = true;
        jobEntity.canonicalClassName = jobClass.getCanonicalName();
        // Maybe this could be fixed, but the `JobPriorityService` will clean this up when the
        // application is ready.
        jobEntity.priority = 0;
        return jobEntity;
    }

    public JobClassAndId toRecord() {
        return new JobClassAndId(
                Objects.requireNonNull(id), Objects.requireNonNull(canonicalClassName));
    }

    public String canonicalClassName() {
        return Objects.requireNonNull(canonicalClassName);
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int priority() {
        return priority;
    }
}
