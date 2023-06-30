package ch.ubique.backend.job.model.client.info;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record JobSystemSchedulerGroupInfo(
        @NotBlank String name,
        @PositiveOrZero int numJobs,
        @PositiveOrZero int numCurrentlyExecuting,
        @PositiveOrZero long queueSize) {}
