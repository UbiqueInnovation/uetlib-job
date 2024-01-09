package ch.ubique.backend.job.model.entity;

import jakarta.persistence.Column;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;

public class JobPropertyId implements Serializable {

    @Column(name = "fk_job_id", nullable = false)
    private Integer jobId;

    @Column(name = "key", nullable = false)
    private String key;

    public static JobPropertyId of(final int jobId, final String key) {
        final var id = new JobPropertyId();
        id.jobId = jobId;
        id.key = key;
        return id;
    }

    public Integer getJobId() {
        return jobId;
    }

    public void setJobId(final Integer jobId) {
        this.jobId = jobId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(final String key) {
        this.key = key;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final var that = (JobPropertyId) o;

        return new EqualsBuilder().append(jobId, that.jobId).append(key, that.key).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(jobId).append(key).toHashCode();
    }
}
