package ch.ubique.backend.job.model;

import ch.ubique.backend.job.model.properties.JobProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record JobRunContext(
        @NotNull @Valid JobClassAndId job,
        @NotNull @Valid JobRun jobRun,
        @NotNull @Valid JobRunArguments arguments,
        @NotNull @Valid JobProperties properties) {}
