package ch.ubique.backend.job.model.triggers;

import jakarta.validation.constraints.NotNull;

import java.util.regex.Pattern;

public record FileTrigger(
        @NotNull String folder, @NotNull Pattern fileRegex, @NotNull FileChangeEvent changeEvent)
        implements TriggerDefinition {}
