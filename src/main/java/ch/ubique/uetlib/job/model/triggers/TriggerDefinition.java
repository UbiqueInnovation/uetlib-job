package ch.ubique.uetlib.job.model.triggers;

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
    @JsonSubTypes.Type(value = DatabaseSubmissionTrigger.class, name = "DATABASE_SUBMISSION"),
    @JsonSubTypes.Type(value = PgListenTrigger.class, name = "PG_LISTEN"),
    @JsonSubTypes.Type(value = RetryTrigger.class, name = "RETRY"),
    @JsonSubTypes.Type(value = FileTrigger.class, name = "FILE")
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface TriggerDefinition
        permits CronTrigger,
                DatabaseSubmissionTrigger,
                FileTrigger,
                FixedRateTrigger,
                JobRunStateTrigger,
                PgListenTrigger,
                RetryTrigger,
                RunOnStartupTrigger {

    @NotNull
    @JsonProperty(required = true)
    default TriggerType type() {
        return TriggerType.getTypeForClass(getClass());
    }
}
