package ch.ubique.backend.job.k8s;

import org.springframework.context.ApplicationContext;

public interface CustomKubernetesJobCreator {

    KubernetesJob createKubernetesJob(ApplicationContext applicationContext);
}
