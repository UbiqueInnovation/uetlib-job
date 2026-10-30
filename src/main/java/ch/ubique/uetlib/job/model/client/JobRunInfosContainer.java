package ch.ubique.uetlib.job.model.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * @param jobs
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobRunInfosContainer(
        @JsonProperty(required = true) @NotNull List<JobRunInfo> jobRuns,
        @JsonProperty(required = true) @NotNull Map<Integer, JobInfo> jobs,
        @JsonProperty(required = true) boolean first,
        @JsonProperty(required = true) boolean last,
        @JsonProperty(required = true) int totalPages) {}
