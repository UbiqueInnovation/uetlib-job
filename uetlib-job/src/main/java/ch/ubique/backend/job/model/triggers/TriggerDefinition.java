package ch.ubique.backend.job.model.triggers;

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
    @JsonSubTypes.Type(value = CronTrigger.class, name = "CRON"),
    @JsonSubTypes.Type(value = RunOnStartupTrigger.class, name = "STARTUP"),
    @JsonSubTypes.Type(value = FixedRateTrigger.class, name = "FIXED_RATE"),
    @JsonSubTypes.Type(value = JobRunStateTrigger.class, name = "JOB_RUN_STATE"),
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface TriggerDefinition
        permits CronTrigger, FixedRateTrigger, JobRunStateTrigger, RunOnStartupTrigger {

    @NotNull
    @JsonProperty
    default TriggerType type() {
        return TriggerType.getTypeForClass(getClass());
    }
}
