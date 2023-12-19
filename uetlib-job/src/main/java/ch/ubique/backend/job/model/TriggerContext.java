package ch.ubique.backend.job.model;

public sealed interface TriggerContext permits EmptyTriggerContext, FileTriggerContext {
    /**
     * Serialize the trigger context type in order to be able to deserialize it later based on its
     * type.
     *
     * @return the type of the trigger context
     */
    default String type() {
        return getClass().getCanonicalName();
    }
}
