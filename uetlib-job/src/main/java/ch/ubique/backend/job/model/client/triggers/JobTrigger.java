package ch.ubique.backend.job.model.client.triggers;

import ch.ubique.backend.job.model.triggers.Trigger;
import ch.ubique.backend.job.model.triggers.TriggerDefinition;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

public record JobTrigger(@Nullable Integer id, @NotNull TriggerDefinition definition) {
    public JobTrigger(final Trigger trigger) {
        this(trigger.id(), trigger.definition());
    }
}
