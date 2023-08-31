package ch.ubique.backend.job.model.client;


import com.github.zafarkhaja.semver.Version;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;

/**
 * @param backendLibVersion deprecated, use {@link #artifacts}
 */
public record InfoResponse(
        @NotBlank Version projectVersion,
        @NotBlank Version backendLibVersion,
        @NotNull Collection<Artifact> artifacts) {}
