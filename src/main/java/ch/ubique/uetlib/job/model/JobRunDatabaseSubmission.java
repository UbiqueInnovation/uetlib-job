package ch.ubique.uetlib.job.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record JobRunDatabaseSubmission(
        @JsonProperty(required = true) int id,
        @JsonProperty(required = true) int jobId,
        @JsonProperty(required = true) @NotNull @Past Instant createdAt,
        @Past @Nullable Instant submittedAt,
        @JsonProperty(required = true) @NotNull @Valid
                JobRunDatabaseSubmissionArguments jobRunDatabaseSubmissionArguments) {}
