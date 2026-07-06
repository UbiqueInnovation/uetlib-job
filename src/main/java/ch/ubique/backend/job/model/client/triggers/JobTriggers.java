package ch.ubique.backend.job.model.client.triggers;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;

public record JobTriggers(
        @JsonProperty(required = true) boolean isFixed,
        @JsonProperty(required = true) @NotNull Collection<@Valid JobTrigger> triggers) {}
