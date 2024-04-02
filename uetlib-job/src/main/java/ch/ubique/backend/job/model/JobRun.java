package ch.ubique.backend.job.model;

import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
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
        @NotNull JobRunnerKey runnerKey,
        @Nullable @Valid JobRunArguments jobRunArguments) {

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
        private JobRunnerKey runnerKey;
        private JobRunArguments jobRunArguments;

        private ModifiableJobRun(final JobRun jobRun) {
            this.id = jobRun.id;
            this.jobId = jobRun.jobId;
            this.jobVersion = jobRun.jobVersion;
            this.startedAt = Objects.requireNonNull(jobRun.startedAt);
            this.finishedAt = jobRun.finishedAt;
            this.state = Objects.requireNonNull(jobRun.state);
            this.runnerKey = Objects.requireNonNull(jobRun.runnerKey);
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

        public ModifiableJobRun runnerKey(@NotNull final JobRunnerKey runnerKey) {
            this.runnerKey = Objects.requireNonNull(runnerKey);
            return this;
        }

        public ModifiableJobRun jobRunArguments(@Nullable final JobRunArguments jobRunArguments) {
            this.jobRunArguments = Objects.requireNonNull(jobRunArguments);
            return this;
        }

        public JobRun toJobRun() {
            return new JobRun(
                    id,
                    jobId,
                    jobVersion,
                    startedAt,
                    finishedAt,
                    state,
                    runnerKey,
                    jobRunArguments);
        }
    }
}
