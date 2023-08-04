package ch.ubique.backend.job.model.client;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobInfosContainer(
        @NotNull List<JobInfo> jobs, @NotNull Map<Integer, JobRunInfo> latestRuns) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JobInfo(
            @NotNull int id,
            @NotNull String name,
            @NotNull String clazz,
            String cronExpression,
            @NotNull boolean cronActive,
            @NotNull boolean cronExpressionValid,
            @NotNull boolean cronFixed,
            @NotNull boolean propertiesFixed,
            @NotNull boolean forceRunnable) {}
}
