package ch.ubique.backend.job.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public sealed interface TriggerContext permits EmptyTriggerContext, FileTriggerContext {
    /**
     * Serialize the trigger context type in order to be able to deserialize it later based on its
     * type.
     *
     * @return the type of the trigger context
     */
    @JsonProperty
    default String type() {
        return getClass().getCanonicalName();
    }
}
