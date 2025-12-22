package ch.ubique.backend.job.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RetryTriggerContext(
        @JsonProperty(defaultValue = "0") int retryCount,
        @JsonProperty(defaultValue = "0") int maxRetries,
        @JsonProperty(defaultValue = "0") int originalJobRunId)
        implements TriggerContext {

    public RetryTriggerContext increment() {
        return new RetryTriggerContext(retryCount + 1, maxRetries, originalJobRunId);
    }

    public boolean canBeRetried() {
        return retryCount < maxRetries;
    }
}
