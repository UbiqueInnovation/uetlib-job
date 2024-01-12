package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobClassAndId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Table(name = "t_job")
@Entity
public class JobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_id", nullable = false)
    private Integer id;

    @Column(name = "clazz", nullable = false)
    private String canonicalClassName;

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
}
