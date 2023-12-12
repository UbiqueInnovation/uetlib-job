package ch.ubique.backend.job.model;

public record EmptyTriggerContext() implements TriggerContext {
    @Override
    public String type() {
        return getClass().getCanonicalName();
    }
}
