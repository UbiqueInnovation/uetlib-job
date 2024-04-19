package ch.ubique.backend.job.model.k8s;

@SuppressWarnings("java:S115") // allow to map easily to JSON and to String
public enum KubernetesPodTolerationEffect {
    NoSchedule,
    PreferNoSchedule,
    NoExecute
}
