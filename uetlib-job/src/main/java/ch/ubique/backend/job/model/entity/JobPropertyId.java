package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobPropertyKey;
import java.io.Serializable;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class JobPropertyId implements Serializable {

    private Integer jobId;
    private JobPropertyKey key;

    public Integer getJobId() {
        return jobId;
    }

    public void setJobId(final Integer jobId) {
        this.jobId = jobId;
    }

    public JobPropertyKey getKey() {
        return key;
    }

    public void setKey(final JobPropertyKey key) {
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
