package ch.ubique.backend.job.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RetryTriggerContext(
        @JsonProperty(required = true) int retryCount,
        @JsonProperty(required = true) int maxRetries,
        @JsonProperty(required = true) int originalJobRunId)
        implements TriggerContext {

    public RetryTriggerContext increment() {
        return new RetryTriggerContext(retryCount + 1, maxRetries, originalJobRunId);
    }

    public boolean canBeRetried() {
        return retryCount < maxRetries;
    }
}
