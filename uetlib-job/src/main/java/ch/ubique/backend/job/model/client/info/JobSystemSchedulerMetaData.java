package ch.ubique.backend.job.model.client.info;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

public record JobSystemSchedulerMetaData(
        @Positive int threadPoolSize,
        @PositiveOrZero int numberOfJobsExecuted,
        @NotBlank String schedulerClass,
        @NotBlank String threadPoolClass,
        @NotBlank String version,
        @NotNull Instant runningSince) {}
