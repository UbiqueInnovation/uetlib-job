package ch.ubique.backend.job.model.client;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobInfosContainer(
        @NotNull List<JobInfo> jobs,
        @NotNull Map<Integer, JobRunInfo> latestRuns,
        @NotNull boolean first,
        @NotNull boolean last,
        @NotNull int totalPages) {}
