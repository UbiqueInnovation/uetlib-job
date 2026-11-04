package ch.uetlib.job.model.client.info;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

import java.time.ZoneId;

public record JobSystemCronInfo(@JsonProperty(required = true) @NotNull ZoneId zoneId) {}
