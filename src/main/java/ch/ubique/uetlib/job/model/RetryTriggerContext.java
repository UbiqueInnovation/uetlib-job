package ch.ubique.uetlib.job.model;

public record RetryTriggerContext(int retryCount, int maxRetries, int originalJobRunId)
        implements TriggerContext {

    public RetryTriggerContext increment() {
        return new RetryTriggerContext(retryCount + 1, maxRetries, originalJobRunId);
    }

    public boolean canBeRetried() {
        return retryCount < maxRetries;
    }
}
