package ch.ubique.backend.job.model.client;

import com.drew.lang.annotations.NotNull;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@NullMarked
public record JobInfosContainer(@NotNull List<JobInfo> jobs, @NotNull Map<Integer, JobRunInfo> latestRuns) {}
