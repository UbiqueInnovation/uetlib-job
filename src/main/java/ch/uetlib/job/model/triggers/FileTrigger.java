package ch.uetlib.job.model.triggers;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.regex.Pattern;

public record FileTrigger(
        @JsonProperty(required = true) @NotBlank String folder,
        @JsonProperty(required = true) @NotNull Pattern fileRegex,
        @JsonProperty(required = true) @NotNull FileChangeEvent changeEvent)
        implements TriggerDefinition {}
