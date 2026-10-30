package ch.ubique.uetlib.job.model.triggers;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

public record FixedRateTrigger(
        @JsonProperty(required = true) @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING)
                Duration period,
        @Nullable @JsonFormat(shape = JsonFormat.Shape.STRING) Duration initialDelay)
        implements TriggerDefinition {
    public FixedRateTrigger(final Duration period) {
        this(period, null);
    }
}
