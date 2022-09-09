package ch.ubique.backend.job.model;

public interface JobRunInput {

    Integer getId();
    void setId(Integer runId);

    Integer getRunId();
    void setRunId(Integer runId);

    String getName();
    void setName(String name);

    String getHash();
    void setHash(String hash);
}
