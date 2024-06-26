package ch.ubique.backend.job.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record JobRunStateTransition(
        @Positive int id,
        @NotNull @Valid JobRun jobRun,
        @NotNull JobRunState state,
        @NotNull @PastOrPresent Instant transitionedAt) {}
