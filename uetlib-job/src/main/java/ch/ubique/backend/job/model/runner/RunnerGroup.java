package ch.ubique.backend.job.model.runner;

import ch.ubique.backend.job.Job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Collection;
import java.util.Set;

public record RunnerGroup(
        @NotBlank String identifier,
        @Positive int threadPoolSize,
        @NotNull Collection<Class<? extends Job>> allowedJobs) {

    public RunnerGroup(@NotBlank final String identifier, @Positive final int threadPoolSize) {
        this(identifier, threadPoolSize, Set.of());
    }
}
