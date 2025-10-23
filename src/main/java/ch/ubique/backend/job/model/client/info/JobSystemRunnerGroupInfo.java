package ch.ubique.backend.job.model.client.info;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobSystemRunnerGroupInfo(
        // Runner group
        @NotBlank String identifier,
        @Positive int threadPoolSize,
        @NotNull List<String> allowedJobs,
        @NotNull List<String> configuredJobs,

        // Thread pool executor
        int busyThreads,
        int queueLength,
        List<String> queueElements,
        @PositiveOrZero long completedTaskCount,
        @PositiveOrZero int largestPoolSize,
        @PositiveOrZero int currentPoolSize) {}
