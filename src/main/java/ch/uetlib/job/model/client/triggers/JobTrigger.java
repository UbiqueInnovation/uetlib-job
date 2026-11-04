package ch.uetlib.job.model.client.triggers;

import ch.uetlib.job.model.triggers.Trigger;
import ch.uetlib.job.model.triggers.TriggerDefinition;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

public record JobTrigger(@Nullable Integer id, @NotNull @Valid TriggerDefinition definition) {
    public JobTrigger(final Trigger trigger) {
        this(trigger.id(), trigger.definition());
    }
}
