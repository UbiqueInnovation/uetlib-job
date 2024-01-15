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
        @NotNull JobRunState state,
        @Nullable JobRunArguments jobRunArguments) {

    public Duration runDuration() {
        final var end = Objects.requireNonNullElse(finishedAt(), Instant.now());
        return Duration.between(startedAt(), end);
    }

    public String readableId() {
        return String.format("Run_%06d", id());
    }

    public ModifiableJobRun modify() {
        return new ModifiableJobRun(this);
    }

    public static class ModifiableJobRun {
        private int id;
        private int jobId;
        private int jobVersion;
        private Instant startedAt;
        private Instant finishedAt;
        private JobRunState state;
        private JobRunArguments jobRunArguments;

        private ModifiableJobRun(final JobRun jobRun) {
            this.id = jobRun.id;
            this.jobId = jobRun.jobId;
            this.jobVersion = jobRun.jobVersion;
            this.startedAt = Objects.requireNonNull(jobRun.startedAt);
            this.finishedAt = jobRun.finishedAt;
            this.state = Objects.requireNonNull(jobRun.state);
            this.jobRunArguments = jobRun.jobRunArguments;
        }

        public ModifiableJobRun id(final int id) {
            this.id = id;
            return this;
        }

        public ModifiableJobRun jobId(final int jobId) {
            this.jobId = jobId;
            return this;
        }

        public ModifiableJobRun jobVersion(final int jobVersion) {
            this.jobVersion = jobVersion;
            return this;
        }

        public ModifiableJobRun startedAt(@NotNull final Instant startedAt) {
            this.startedAt = Objects.requireNonNull(startedAt);
            return this;
        }

        public ModifiableJobRun finishedAt(@Nullable final Instant finishedAt) {
            this.finishedAt = finishedAt;
            return this;
        }

        public ModifiableJobRun state(@NotNull final JobRunState state) {
            this.state = Objects.requireNonNull(state);
            return this;
        }

        public ModifiableJobRun jobRunArguments(@Nullable final JobRunArguments jobRunArguments) {
            this.jobRunArguments = Objects.requireNonNull(jobRunArguments);
            return this;
        }

        public JobRun toJobRun() {
            return new JobRun(id, jobId, jobVersion, startedAt, finishedAt, state, jobRunArguments);
        }
    }
}
