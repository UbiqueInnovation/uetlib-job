package ch.uetlib.job.model.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobQueueInfosContainer(
        @JsonProperty(required = true) @NotNull List<JobQueueEntryInfo> elements,
        @JsonProperty(required = true) @PositiveOrZero long length,
        @Nullable JobQueueEntryInfo oldest,
        @Nullable JobQueueEntryInfo newest,
        @JsonProperty(required = true) boolean first,
        @JsonProperty(required = true) boolean last,
        @JsonProperty(required = true) int totalPages) {}
