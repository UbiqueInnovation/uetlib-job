package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobPropertyKey;
import java.io.Serializable;

public class JobPropertyId implements Serializable {

    private Integer jobId;
    private JobPropertyKey key;

    public Integer getJobId() {
        return jobId;
    }

    public JobPropertyId setJobId(final Integer jobId) {
        this.jobId = jobId;
        return this;
    }

    public JobPropertyKey getKey() {
        return key;
    }

    public JobPropertyId setKey(final JobPropertyKey key) {
        this.key = key;
        return this;
    }
}
