package ch.ubique.backend.job.model.client;

import com.drew.lang.annotations.NotNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobInfosContainer(
        @JsonProperty(required = true) @NotNull @Valid List<JobInfo> jobs,
        @JsonProperty(required = true) @NotNull @Valid Map<Integer, JobRunInfo> latestRuns) {}
