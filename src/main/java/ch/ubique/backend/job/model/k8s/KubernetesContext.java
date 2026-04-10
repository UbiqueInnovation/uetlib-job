package ch.ubique.backend.job.model.k8s;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Pod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

public record KubernetesContext(
        @NotNull ApiClient apiClient,
        @NotBlank String currentNamespace,
        @Nullable V1Pod currentPod) {}
