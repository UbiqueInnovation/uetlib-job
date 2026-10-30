package ch.ubique.uetlib.job.model.k8s;

import ch.ubique.uetlib.job.model.JobRunContext;

public record KubernetesJobRunContext(
        JobRunContext jobRunContext, KubernetesContext kubernetesContext) {}
