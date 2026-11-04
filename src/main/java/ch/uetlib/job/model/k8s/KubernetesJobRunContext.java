package ch.uetlib.job.model.k8s;

import ch.uetlib.job.model.JobRunContext;

public record KubernetesJobRunContext(
        JobRunContext jobRunContext, KubernetesContext kubernetesContext) {}
