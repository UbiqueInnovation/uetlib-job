package ch.ubique.backend.job.model;

import ch.ubique.backend.job.model.triggers.TriggerContextType;

import com.fasterxml.jackson.annotation.*;

import jakarta.validation.constraints.NotNull;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = EmptyTriggerContext.class, name = "EMPTY"),
    @JsonSubTypes.Type(value = FileTriggerContext.class, name = "FILE"),
    @JsonSubTypes.Type(value = RetryTriggerContext.class, name = "RETRY"),
})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface TriggerContext
        permits EmptyTriggerContext, FileTriggerContext, RetryTriggerContext {
    /**
     * Serialize the trigger context type in order to be able to deserialize it later based on its
     * type.
     *
     * @return the type of the trigger context
     */
    @NotNull
    @JsonProperty
    default String type() {
        return TriggerContextType.getTypeForClass(getClass()).name();
    }
}
