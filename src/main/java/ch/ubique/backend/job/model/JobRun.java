package ch.ubique.backend.job.model;

import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.triggers.TriggerType;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record JobRun(
        int id,
        int jobId,
        @NotBlank String versionTag,
        @NotNull Instant startedAt,
        @Nullable Instant finishedAt,
        @NotNull JobRunState latestState,
        @NotNull JobRunnerKey runnerKey,
        @Nullable TriggerType triggerType,
        @Nullable @Valid JobRunArguments jobRunArguments) {

    public Duration runDuration() {
        final var end = Objects.requireNonNullElse(finishedAt(), Instant.now());
        return Duration.between(startedAt(), end);
    }

    public String readableId() {
        return readableId(id);
    }

    public static String readableId(final int id) {
        return String.format("Run_%06d", id);
    }

    public ModifiableJobRun modify() {
        return new ModifiableJobRun(this);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("jobId", jobId)
                .append("latestState", latestState)
                .toString();
    }

    public static final class ModifiableJobRun {

        private int id;
        private int jobId;
        private final String versionTag;
        private Instant startedAt;
        private Instant finishedAt;
        private JobRunState state;
        private JobRunnerKey runnerKey;
        private final TriggerType triggerType;
        private JobRunArguments jobRunArguments;

        private ModifiableJobRun(final JobRun jobRun) {
            this.id = jobRun.id;
            this.jobId = jobRun.jobId;
            this.versionTag = jobRun.versionTag;
            this.startedAt = Objects.requireNonNull(jobRun.startedAt);
            this.finishedAt = jobRun.finishedAt;
            this.state = Objects.requireNonNull(jobRun.latestState);
            this.runnerKey = Objects.requireNonNull(jobRun.runnerKey);
            this.triggerType = jobRun.triggerType;
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
            this.jobRunArguments = jobRunArguments;
            return this;
        }

        public JobRun toJobRun() {
            return new JobRun(
                    id,
                    jobId,
                    versionTag,
                    startedAt,
                    finishedAt,
                    state,
                    runnerKey,
                    triggerType,
                    jobRunArguments);
        }
    }
}
