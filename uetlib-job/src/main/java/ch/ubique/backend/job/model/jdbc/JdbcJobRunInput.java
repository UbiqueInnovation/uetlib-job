package ch.ubique.backend.job.model.jdbc;

import ch.ubique.backend.job.model.JobRunInput;

public class JdbcJobRunInput implements JobRunInput {

    private Integer id;
    private Integer runId;
    private String name;
    private String hash;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public Integer getRunId() {
        return runId;
    }

    @Override
    public void setRunId(Integer runId) {
        this.runId = runId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getHash() {
        return hash;
    }

    @Override
    public void setHash(String hash) {
        this.hash = hash;
    }
}
