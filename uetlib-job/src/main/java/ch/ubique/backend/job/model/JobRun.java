package ch.ubique.backend.job.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public interface JobRun {

    default ZonedDateTime getStartedAtZoned() {
        final var instant = getStartedAt();
        if (instant == null) {
            return null;
        }
        return instant.atZone(ZoneId.of("Europe/Zurich"));
    }

    default ZonedDateTime getFinishedAtZoned() {
        final var instant = getFinishedAt();
        if (instant == null) {
            return null;
        }
        return instant.atZone(ZoneId.of("Europe/Zurich"));
    }

    Instant getFinishedAt();

    void setFinishedAt(Instant finishedAt);

    Instant getStartedAt();

    void setStartedAt(Instant startedAt);

    String getLogOutput();

    void setLogOutput(String logOutput);

    JobRunState getState();

    void setState(JobRunState state);

    Integer getJobId();

    void setJobId(Integer jobId);

    Integer getJobVersion();

    void setJobVersion(Integer jobVersion);

    Integer getId();

    void setId(Integer id);
}
