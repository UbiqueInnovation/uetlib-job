package ch.ubique.backend.job.model.client;

import jakarta.validation.constraints.NotBlank;

public record InfoResponse(@NotBlank String projectVersion, @NotBlank String backendLibVersion) {}
