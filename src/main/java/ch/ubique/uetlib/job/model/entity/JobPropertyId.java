package ch.ubique.uetlib.job.model.entity;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

public class JobPropertyId implements Serializable {

    private @Nullable Integer jobId;
    private @Nullable String key;

    @Nullable
    public Integer getJobId() {
        return jobId;
    }

    public void setJobId(final Integer jobId) {
        this.jobId = jobId;
    }

    @Nullable
    public String getKey() {
        return key;
    }

    public void setKey(final String key) {
        this.key = key;
    }

    @Override
    public boolean equals(final @Nullable Object o) {
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
