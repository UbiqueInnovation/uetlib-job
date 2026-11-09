package ch.uetlib.job.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record JobRunStateTransition(
        @JsonProperty(required = true) @Positive int id,
        @JsonProperty(required = true) @NotNull @Valid JobRun jobRun,
        @JsonProperty(required = true) @NotNull JobRunState state,
        @JsonProperty(required = true) @NotNull @PastOrPresent Instant transitionedAt) {}
