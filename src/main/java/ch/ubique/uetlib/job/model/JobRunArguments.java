package ch.ubique.uetlib.job.model;

import ch.ubique.uetlib.job.model.triggers.TriggerType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobRunArguments(
        @JsonProperty(required = true) boolean forceRun,
        @Nullable TriggerType triggerType,
        @Nullable @Valid TriggerContext triggerContext,
        @Nullable @Valid ReplayContext replayContext,
        @Nullable JobRunArgumentsAdditionalData additionalData) {

    public static final JobRunArguments DEFAULT = new JobRunArguments(false);

    public JobRunArguments() {
        this(false);
    }

    public JobRunArguments(final boolean forceRun) {
        this(forceRun, null);
    }

    public JobRunArguments(
            final boolean forceRun, @Nullable final JobRunArgumentsAdditionalData additionalData) {
        this(forceRun, null, null, null, additionalData);
    }

    @JsonIgnore
    public JobRunArguments withReplayContext(final ReplayContext replayContext) {
        return new JobRunArguments(
                forceRun, triggerType, triggerContext, replayContext, additionalData);
    }

    @Override
    public String toString() {
        final var builder =
                new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                        .append("forceRun", forceRun);

        if (triggerType != null) {
            builder.append("triggerType", triggerType);
        }
        if (replayContext != null) {
            builder.append("replayContext", replayContext);
        }
        if (additionalData != null) {
            builder.append("additionalData", additionalData);
        }
        if (triggerContext != null) {
            builder.append("triggerContext", triggerContext);
        }

        return builder.toString();
    }
}
