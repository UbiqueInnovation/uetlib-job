package ch.ubique.backend.job.k8s;

import static java.util.Objects.requireNonNull;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.k8s.KubernetesJobRunContext;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.*;

public abstract class KubernetesJob implements Job {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final ApplicationContext applicationContext;

    protected KubernetesJob(final ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public static KubernetesJob forJob(final Job job, final ApplicationContext applicationContext) {
        if (job instanceof final KubernetesJob kubernetesJob) {
            return kubernetesJob;
        }
        return new KubernetesJobWrapper(applicationContext, job);
    }

    public ApiClient apiClient() throws IOException {
        return Config.defaultClient();
    }

    @Override
    public final JobRunState run(final JobRunContext jobRunContext) throws Exception {
        final var jobRunnerKey =
                jobRunContext.properties().get(JobPropertyDefinitions.RUNNER).value();
        if (jobRunnerKey != JobRunnerKey.DEDICATED_K8S_JOB) {
            logger.error(
                    "The runner must be set to {} but is currently set to {}",
                    JobRunnerKey.DEDICATED_K8S_JOB,
                    jobRunnerKey);
        }

        logger.error(
                "This method should never be called. Please make sure this job is scheduled as"
                    + " dedicated k8s job using the specified docker image.");
        return JobRunState.ERROR;
    }

    public final V1Job job(final KubernetesJobRunContext kubernetesJobRunContext) {
        return new V1Job()
                .metadata(jobMetadata(kubernetesJobRunContext))
                .spec(jobSpec(kubernetesJobRunContext));
    }

    protected V1ObjectMeta jobMetadata(final KubernetesJobRunContext kubernetesJobRunContext) {
        // Add annotations to make Prometheus scrape our job
        final var annotations =
                Map.ofEntries(
                        Map.entry("prometheus.io/path", "/actuator/prometheus"),
                        Map.entry("prometheus.io/port", "8085"),
                        Map.entry("prometheus.io/scrape", "true"));

        return new V1ObjectMeta()
                .namespace(namespace(kubernetesJobRunContext))
                .name(jobName(kubernetesJobRunContext))
                .annotations(annotations);
    }

    protected V1JobSpec jobSpec(final KubernetesJobRunContext kubernetesJobRunContext) {
        final var ttl =
                kubernetesJobRunContext
                        .jobRunContext()
                        .properties()
                        .get(JobPropertyDefinitions.K8S_TTL_AFTER_FINISHED)
                        .value();

        return new V1JobSpec()
                .ttlSecondsAfterFinished((int) ttl.toSeconds())
                .template(new V1PodTemplateSpec().spec(podSpec(kubernetesJobRunContext)));
    }

    private static String namespace(final KubernetesJobRunContext kubernetesJobRunContext) {
        return kubernetesJobRunContext.kubernetesContext().currentNamespace();
    }

    protected String jobName(final KubernetesJobRunContext kubernetesJobRunContext) {
        return this.applicationContext.getEnvironment().getProperty("spring.application.name")
                + "-"
                + kubernetesJobRunContext.jobRunContext().job().name().toLowerCase()
                + "-run-"
                + kubernetesJobRunContext.jobRunContext().jobRun().id();
    }

    protected V1PodSpec podSpec(final KubernetesJobRunContext kubernetesJobRunContext) {
        final var podSpec =
                new V1PodSpec()
                        .imagePullSecrets(imagePullSecrets(kubernetesJobRunContext))
                        .volumes(volumes(kubernetesJobRunContext))
                        .nodeSelector(nodeSelector(kubernetesJobRunContext))
                        .restartPolicy("Never");

        containers(kubernetesJobRunContext).forEach(podSpec::addContainersItem);
        toleration(kubernetesJobRunContext).ifPresent(podSpec::addTolerationsItem);

        return podSpec;
    }

    protected List<V1LocalObjectReference> imagePullSecrets(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        return requireNonNull(kubernetesJobRunContext.kubernetesContext().currentPod().getSpec())
                .getImagePullSecrets();
    }

    protected List<V1Volume> volumes(final KubernetesJobRunContext kubernetesJobRunContext) {
        return requireNonNull(kubernetesJobRunContext.kubernetesContext().currentPod().getSpec())
                .getVolumes();
    }

    protected Map<String, String> nodeSelector(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        return requireNonNull(kubernetesJobRunContext.kubernetesContext().currentPod().getSpec())
                .getNodeSelector();
    }

    protected abstract Collection<V1Container> containers(
            final KubernetesJobRunContext kubernetesJobRunContext);

    protected Optional<V1Toleration> toleration(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        return kubernetesJobRunContext
                .jobRunContext()
                .properties()
                .get(JobPropertyDefinitions.K8S_SKU)
                .valueMaybe()
                .map(
                        value ->
                                new V1Toleration()
                                        .key("sku")
                                        .operator("Equal")
                                        .value(value)
                                        .effect("NoSchedule"));
    }

    protected V1ResourceRequirements resources(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        final var cpuRequests =
                Quantity.fromString(
                        kubernetesJobRunContext
                                .jobRunContext()
                                .properties()
                                .get(JobPropertyDefinitions.K8S_REQUESTS_CPU)
                                .value());

        final var memoryRequests =
                Quantity.fromString(
                        kubernetesJobRunContext
                                .jobRunContext()
                                .properties()
                                .get(JobPropertyDefinitions.K8S_REQUESTS_MEMORY)
                                .value());

        final var requests =
                Map.ofEntries(Map.entry("cpu", cpuRequests), Map.entry("memory", memoryRequests));
        final var limits = Map.ofEntries(Map.entry("memory", memoryRequests));
        return new V1ResourceRequirements().requests(requests).limits(limits);
    }
}
