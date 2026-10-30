package ch.ubique.uetlib.job.model.client.info;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record JobSystemInfoResponse(
        @JsonProperty(required = true) @NotNull @Valid JobSystemInfo info) {}
