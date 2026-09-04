package ch.uetlib.job.model.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * @param instances the primary lease holder first, then newest first
 */
public record JobInstanceInfosContainer(
        @JsonProperty(required = true) @NotNull List<JobInstanceInfo> instances) {}
