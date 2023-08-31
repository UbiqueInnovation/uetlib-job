package ch.ubique.backend.job.model.client;

import com.github.zafarkhaja.semver.Version;

import jakarta.validation.constraints.NotBlank;

public record Artifact(
        @NotBlank String groupId, @NotBlank String artifactId, @NotBlank Version version) {}
