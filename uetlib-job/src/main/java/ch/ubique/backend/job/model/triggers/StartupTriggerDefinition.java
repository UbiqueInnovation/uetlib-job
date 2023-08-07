package ch.ubique.backend.job.model.triggers;

import jakarta.validation.constraints.NotNull;

public record StartupTriggerDefinition() implements TriggerDefinition {

    @Override
    @NotNull
    public TriggerType type() {
        return TriggerType.STARTUP;
    }
}
