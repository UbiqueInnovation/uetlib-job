package ch.ubique.uetlib.job.model.client;

import com.drew.lang.annotations.NotNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobInfosContainer(
        @JsonProperty(required = true) @NotNull List<@Valid JobInfo> jobs,
        @JsonProperty(required = true) @NotNull
                Map<@Valid @PositiveOrZero Integer, @Valid JobRunInfo> latestRuns) {}
