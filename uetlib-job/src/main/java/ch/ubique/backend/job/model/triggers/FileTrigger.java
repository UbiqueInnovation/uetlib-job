package ch.ubique.backend.job.model.triggers;

import jakarta.validation.constraints.NotNull;

public record FileTrigger(
        @NotNull String folder, @NotNull String fileRegex, @NotNull FileChangeEvent changeEvent)
        implements TriggerDefinition {}
