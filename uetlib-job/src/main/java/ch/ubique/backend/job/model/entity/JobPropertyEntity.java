package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobPropertyKey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Table(name = "t_job_properties")
@Entity
@IdClass(JobPropertyId.class)
public class JobPropertyEntity {

    @Id
    @Column(name = "fk_job_id", nullable = false)
    private int jobId;

    @Id
    @Column(name = "key", nullable = false)
    @Enumerated(EnumType.STRING)
    private JobPropertyKey key;

    @Column(name = "value")
    private String value;

    public int getJobId() {
        return jobId;
    }

    public JobPropertyEntity setJobId(final Integer jobId) {
        this.jobId = jobId;
        return this;
    }

    public JobPropertyKey getKey() {
        return key;
    }

    public JobPropertyEntity setKey(final JobPropertyKey key) {
        this.key = key;
        return this;
    }

    public String getValue() {
        return value;
    }

    public JobPropertyEntity setValue(final String value) {
        this.value = value;
        return this;
    }
}
