package ch.ubique.backend.job.model;

import java.time.Instant;

public interface JobRun {

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
