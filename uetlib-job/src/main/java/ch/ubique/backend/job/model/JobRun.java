package ch.ubique.backend.job.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public interface JobRun {

    Instant getFinishedAt();

    void setFinishedAt(Instant finishedAt);

    Instant getStartedAt();

    void setStartedAt(Instant startedAt);

    JobRunState getState();

    void setState(JobRunState state);

    Integer getJobId();

    void setJobId(Integer jobId);

    Integer getJobVersion();

    void setJobVersion(Integer jobVersion);

    Integer getId();

    void setId(Integer id);

    default Duration runDuration() {
        final var end = Objects.requireNonNullElse(getFinishedAt(), Instant.now());
        return Duration.between(getStartedAt(), end);
    }

    default String readableId() {
        return String.format("Run_%06d", getId());
    }
}
