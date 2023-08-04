package ch.ubique.backend.job.model;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record JobRun(
        int id,
        int jobId,
        int jobVersion,
        @NotNull Instant startedAt,
        @Nullable Instant finishedAt,
        @NotNull JobRunState state) {

    public Duration runDuration() {
        final var end = Objects.requireNonNullElse(finishedAt(), Instant.now());
        return Duration.between(startedAt(), end);
    }

    public String readableId() {
        return String.format("Run_%06d", id());
    }
}
