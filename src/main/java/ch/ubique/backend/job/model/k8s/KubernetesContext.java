package ch.ubique.backend.job.model.k8s;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Pod;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record KubernetesContext(
        @NotNull ApiClient apiClient,
        @NotBlank String currentNamespace,
        @Nullable V1Pod currentPod) {}
