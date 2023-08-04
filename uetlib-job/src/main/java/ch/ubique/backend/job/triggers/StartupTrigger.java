package ch.ubique.backend.job.triggers;

import jakarta.validation.constraints.NotNull;

public record StartupTrigger() implements Trigger {

    @Override
    @NotNull
    public String type() {
        return "startup";
    }
}
