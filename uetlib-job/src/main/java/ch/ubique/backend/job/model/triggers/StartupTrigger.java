package ch.ubique.backend.job.model.triggers;

import jakarta.validation.constraints.NotNull;

public record StartupTrigger() implements Trigger {

    @Override
    @NotNull
    public TriggerType type() {
        return TriggerType.STARTUP;
    }
}
