package ch.ubique.backend.job.model.entity;

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public JobEntity() {}

    public JobEntity(final String canonicalClassName) {
        this.canonicalClassName = canonicalClassName;
    }

    public JobEntity(final JobClassAndId jobClassAndId) {
        this.id = jobClassAndId.id();
        this.canonicalClassName = jobClassAndId.canonicalClassName();
    }

    public JobClassAndId toRecord() {
        return new JobClassAndId(id, canonicalClassName);
    }

    public JobEntity with(final String canonicalClassName) {
        this.canonicalClassName = canonicalClassName;
        return this;
    }
}
