package ch.ubique.backend.job.model.jdbc;

import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import java.time.Instant;

public class JdbcJobRun implements JobRun {

    private Integer id;

    private Integer jobId;

    private Integer jobVersion;

    private JobRunState state;

    private String logOutput;

    private Instant startedAt;

    private Instant finishedAt;

    @Override
    public Instant getFinishedAt() {
        return finishedAt;
    }

    @Override
    public void setFinishedAt(final Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    @Override
    public Instant getStartedAt() {
        return startedAt;
    }

    @Override
    public void setStartedAt(final Instant startedAt) {
        this.startedAt = startedAt;
    }

    @Override
    public String getLogOutput() {
        return logOutput;
    }

    @Override
    public void setLogOutput(final String logOutput) {
        this.logOutput = logOutput;
    }

    @Override
    public JobRunState getState() {
        return state;
    }

    @Override
    public void setState(final JobRunState state) {
        this.state = state;
    }

    @Override
    public Integer getJobId() {
        return jobId;
    }

    @Override
    public void setJobId(final Integer jobId) {
        this.jobId = jobId;
    }

    @Override
    public Integer getJobVersion() {
        return jobVersion;
    }

    @Override
    public void setJobVersion(Integer jobVersion) {
        this.jobVersion = jobVersion;
    }

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(final Integer id) {
        this.id = id;
    }
}
