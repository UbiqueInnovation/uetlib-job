package ch.ubique.uetlib.job.model;

import org.jspecify.annotations.Nullable;

public interface JobRunInput {

    @Nullable Integer getId();

    void setId(@Nullable Integer runId);

    @Nullable Integer getRunId();

    void setRunId(@Nullable Integer runId);

    @Nullable String getName();

    void setName(@Nullable String name);

    @Nullable String getHash();

    void setHash(@Nullable String hash);
}
