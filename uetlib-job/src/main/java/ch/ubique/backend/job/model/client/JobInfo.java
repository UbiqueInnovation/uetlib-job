package ch.ubique.backend.job.model.client;

import ch.ubique.backend.job.model.properties.enums.JobManualTriggerMode;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobInfo(
        @NotNull int id,
        @NotNull String name,
        @NotNull String clazz,
        String cronExpression,
        @NotNull boolean cronActive,
        @NotNull boolean cronExpressionValid,
        @NotNull boolean cronFixed,
        @NotNull boolean propertiesFixed,
        @NotNull boolean forceRunnable,
        @NotNull JobManualTriggerMode manualTriggerMode) {}
