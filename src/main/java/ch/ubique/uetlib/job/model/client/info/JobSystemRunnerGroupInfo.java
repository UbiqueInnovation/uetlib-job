package ch.ubique.uetlib.job.model.client.info;

import ch.ubique.uetlib.job.service.executor.runners.CurrentRunnerItem;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Collection;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobSystemRunnerGroupInfo(
        // Runner group
        @JsonProperty(required = true) @NotBlank String identifier,
        @JsonProperty(required = true) @Positive int threadPoolSize,
        @JsonProperty(required = true) @NotNull List<String> allowedJobs,
        @JsonProperty(required = true) @NotNull List<String> configuredJobs,

        // Thread pool executor
        @JsonProperty(required = true) int busyThreads,
        @JsonProperty(required = true) int queueLength,
        @JsonProperty(required = true) @NotNull Collection<@Valid CurrentRunnerItem> queueElements,
        @JsonProperty(required = true) @NotNull Collection<@Valid CurrentRunnerItem> activeElements,
        @JsonProperty(required = true) @PositiveOrZero long completedTaskCount,
        @JsonProperty(required = true) @PositiveOrZero int largestPoolSize,
        @JsonProperty(required = true) @PositiveOrZero int currentPoolSize) {}
