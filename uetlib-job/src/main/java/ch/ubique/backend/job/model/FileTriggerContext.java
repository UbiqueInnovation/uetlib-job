package ch.ubique.backend.job.model;

import jakarta.validation.constraints.NotNull;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

public record FileTriggerContext(@NotNull Path path, @NotNull WatchEvent<?> event)
        implements TriggerContext {}
