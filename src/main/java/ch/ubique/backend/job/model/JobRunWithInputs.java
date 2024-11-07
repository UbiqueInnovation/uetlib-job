package ch.ubique.backend.job.model;

import java.time.Instant;
import java.util.Set;

public record JobRunWithInputs(
        int id,
        int jobVersion,
        JobRunState state,
        Instant startetAt,
        Instant finishedAt,
        Set<JobRunInput> inputs) {}
