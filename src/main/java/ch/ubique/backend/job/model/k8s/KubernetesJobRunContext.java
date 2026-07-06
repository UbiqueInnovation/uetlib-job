package ch.ubique.backend.job.model.k8s;

import ch.ubique.backend.job.model.JobRunContext;

public record KubernetesJobRunContext(
        JobRunContext jobRunContext, KubernetesContext kubernetesContext) {}
