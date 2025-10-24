package ch.ubique.backend.job.model;

public record RetryTriggerContext(int retryCount, int originalJobRunId) implements TriggerContext {

    public RetryTriggerContext increment() {
        return new RetryTriggerContext(retryCount + 1, originalJobRunId);
    }
}
