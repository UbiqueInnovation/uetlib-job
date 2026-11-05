package ch.uetlib.job.model.client;

import ch.uetlib.job.model.JobDaemon;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * A live instance, as it last checked in (SCHEDULING.md §1).
 *
 * @param primaryCandidate has the {@code job-primary-triggers} profile, so it may take the lease
 * @param primary holds the primary lease: its triggers queue runs
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobInstanceInfo(
        @JsonProperty(required = true) @NotBlank String name,
        @JsonProperty(required = true) @NotBlank String version,
        @JsonProperty(required = true) @NotNull List<String> taints,
        @JsonProperty(required = true) boolean primaryCandidate,
        @JsonProperty(required = true) boolean primary,
        @Nullable Instant primarySince,
        @Nullable Integer triggerCount,
        @JsonProperty(required = true) @NotNull Instant startedAt,
        @JsonProperty(required = true) @NotNull Instant lastCheckIn) {

    public static JobInstanceInfo from(final JobDaemon instance) {
        return new JobInstanceInfo(
                instance.daemonName(),
                instance.version(),
                instance.taints().stream().sorted().toList(),
                instance.primaryCandidate(),
                instance.holdsPrimaryLease(),
                instance.primarySince(),
                instance.triggerCount(),
                instance.startedAt(),
                instance.updatedAt());
    }
}
