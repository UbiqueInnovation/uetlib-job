package ch.ubique.backend.job.model;

import ch.ubique.backend.job.model.triggers.TriggerContextType;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import jakarta.validation.constraints.NotNull;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = EmptyTriggerContext.class, name = "EMPTY"),
    @JsonSubTypes.Type(value = FileTriggerContext.class, name = "FILE"),
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface TriggerContext permits EmptyTriggerContext, FileTriggerContext {
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
