package ch.ubique.backend.job.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobRunDatabaseSubmissionArguments(
        boolean forceRun, @Nullable Object additionalData) {}
