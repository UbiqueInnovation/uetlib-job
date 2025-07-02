package ch.ubique.backend.job.model;

import ch.ubique.backend.job.model.converters.ObjectWithTypeDeserializer;
import ch.ubique.backend.job.model.converters.ObjectWithTypeSerializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobRunArguments(
        boolean forceRun,
        @NotNull @Valid TriggerContext triggerContext,
        @Nullable ReplayContext replayContext,
        @Nullable
                @JsonSerialize(using = ObjectWithTypeSerializer.class)
                @JsonDeserialize(using = ObjectWithTypeDeserializer.class)
                Object additionalData) {

    public static JobRunArguments DEFAULT = new JobRunArguments(false);

    public JobRunArguments() {
        this(false);
    }

    public JobRunArguments(final boolean forceRun) {
        this(forceRun, null);
    }

    public JobRunArguments(final boolean forceRun, @Nullable final Object additionalData) {
        this(forceRun, new EmptyTriggerContext(), additionalData);
    }

    public JobRunArguments(
            final boolean forceRun,
            final TriggerContext triggerContext,
            final Object additionalData) {
        this(forceRun, triggerContext, null, additionalData);
    }

    @JsonIgnore
    public JobRunArguments withReplayContext(final ReplayContext replayContext) {
        return new JobRunArguments(forceRun, triggerContext, replayContext, additionalData);
    }

    @Override
    public String toString() {
        final var builder =
                new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                        .append("forceRun", forceRun);

        if (replayContext != null) {
            builder.append("replayContext", replayContext);
        }
        if (additionalData != null) {
            builder.append("additionalData", additionalData);
        }
        if (!(triggerContext instanceof EmptyTriggerContext)) {
            builder.append("triggerContext", triggerContext);
        }

        return builder.toString();
    }
}
