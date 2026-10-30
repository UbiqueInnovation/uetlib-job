package ch.ubique.uetlib.job.model;

import java.time.Instant;
import java.util.Set;

public record JobRunWithInputs(
        int id,
        String versionTag,
        JobRunState state,
        Instant startetAt,
        Instant finishedAt,
        Set<JobRunInput> inputs) {}
