package ch.ubique.backend.job.model.client;


import com.github.zafarkhaja.semver.Version;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;

public record InfoResponse(
        @NotBlank Version projectVersion,
        @NotNull Collection<Artifact> artifacts) {}
