package ch.ubique.uetlib.job.model.k8s;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Pod;

import org.jspecify.annotations.Nullable;

public record KubernetesContext(
        ApiClient apiClient, String currentNamespace, @Nullable V1Pod currentPod) {}
