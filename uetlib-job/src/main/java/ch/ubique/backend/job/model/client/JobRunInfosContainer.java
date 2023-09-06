package ch.ubique.backend.job.model.client;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @param jobs
 * @param jobNames @deprecated since v1.5, use {@link #jobs}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobRunInfosContainer(
        @NotNull List<JobRunInfo> jobRuns,
        @NotNull Map<Integer, JobInfo> jobs,
        @Deprecated(since = "1.5") @NotNull Map<Integer, String> jobNames,
        @NotNull boolean first,
        @NotNull boolean last,
        @NotNull int totalPages) {}
