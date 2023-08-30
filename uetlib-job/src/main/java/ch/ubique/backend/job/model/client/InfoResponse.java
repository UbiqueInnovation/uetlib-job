package ch.ubique.backend.job.model.client;

import ch.ubique.backend.core.validator.NullOrNotBlank;

import com.github.zafarkhaja.semver.Version;

import jakarta.validation.constraints.NotBlank;

public record InfoResponse(
        @NotBlank Version projectVersion,
        @NotBlank Version backendLibVersion,
        @NullOrNotBlank Version swisstopoBackendSharedVersion,
        @NullOrNotBlank Version sacBackendSharedVersion) {}
