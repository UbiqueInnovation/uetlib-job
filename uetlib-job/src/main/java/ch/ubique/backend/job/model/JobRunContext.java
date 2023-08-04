package ch.ubique.backend.job.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record JobRunContext(
        @NotNull @Valid JobClassAndId job,
        @NotNull @Valid JobRun jobRun,
        @NotNull @Valid JobRunArguments arguments) {}
