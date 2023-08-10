package ch.ubique.backend.job.model.k8s;

import ch.ubique.backend.job.model.JobRunContext;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record KubernetesJobRunContext(
        @NotNull @Valid JobRunContext jobRunContext,
        @NotNull @Valid KubernetesContext kubernetesContext) {}
