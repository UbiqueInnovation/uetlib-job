package ch.ubique.backend.job.model.client;

import ch.ubique.backend.job.model.properties.enums.JobManualTriggerMode;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.NON_NULL)
@NullMarked
public record JobInfo(
        int id,
        @NotNull String name,
        @NotNull String clazz,
        boolean enabled,
        boolean propertiesFixed,
        boolean triggersFixed,
        int numTriggers,
        boolean forceRunnable,
        @NotNull JobManualTriggerMode manualTriggerMode) {}
