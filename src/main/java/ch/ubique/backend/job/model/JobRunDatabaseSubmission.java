package ch.ubique.backend.job.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.Instant;

public record JobRunDatabaseSubmission(
        int id,
        int jobId,
        @NotNull @Past Instant createdAt,
        @Past Instant submittedAt,
        @NotNull @Valid JobRunDatabaseSubmissionArguments jobRunDatabaseSubmissionArguments) {}
