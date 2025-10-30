package ch.ubique.backend.job.model;

import ch.ubique.backend.job.model.triggers.TriggerType;

import com.fasterxml.jackson.annotation.*;

import jakarta.validation.constraints.NotNull;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FileTriggerContext.class, name = "FILE"),
    @JsonSubTypes.Type(value = RetryTriggerContext.class, name = "RETRY"),
})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface TriggerContext permits FileTriggerContext, RetryTriggerContext {
    /**
     * Serialize the trigger context type in order to be able to deserialize it later based on its
     * type.
     *
     * @return the type of the trigger context
     */
    @NotNull
    @JsonProperty
    default TriggerType type() {
        return switch (this) {
            case final FileTriggerContext ignored -> TriggerType.FILE;
            case final RetryTriggerContext ignored -> TriggerType.RETRY;
        };
    }
}
