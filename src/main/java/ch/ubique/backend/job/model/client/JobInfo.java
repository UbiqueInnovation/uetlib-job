package ch.ubique.backend.job.model.client;

import ch.ubique.backend.job.model.properties.enums.JobManualTriggerMode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobInfo(
        @JsonProperty(required = true) int id,
        @JsonProperty(required = true) @NotNull String name,
        @JsonProperty(required = true) @NotNull String clazz,
        @JsonProperty(required = true) boolean enabled,
        @JsonProperty(required = true) boolean propertiesFixed,
        @JsonProperty(required = true) boolean triggersFixed,
        @JsonProperty(required = true) int numTriggers,
        @JsonProperty(required = true) boolean forceRunnable,
        @JsonProperty(required = true) @NotNull JobManualTriggerMode manualTriggerMode) {}
