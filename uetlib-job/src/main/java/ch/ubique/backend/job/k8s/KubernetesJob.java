package ch.ubique.backend.job.k8s;

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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.*;

public abstract class KubernetesJob implements Job {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final ApplicationContext applicationContext;

    protected KubernetesJob(final ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public static KubernetesJob forJob(final Job job, final ApplicationContext applicationContext) {
        if (job instanceof final KubernetesJob kubernetesJob) {
            return kubernetesJob;
        }
        return new KubernetesJobWrapper(applicationContext, job);
    }

    private static String namespace(final KubernetesJobRunContext kubernetesJobRunContext) {
        return kubernetesJobRunContext.kubernetesContext().currentNamespace();
    }

    private static Optional<V1PodSpec> getCurrentPodSpec(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        return Optional.ofNullable(kubernetesJobRunContext.kubernetesContext().currentPod())
                .map(V1Pod::getSpec);
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

    public V1EnvVar additionalJavaOptsEnvVar(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        final var additionalJavaOpts =
                kubernetesJobRunContext
                        .jobRunContext()
                        .properties()
                        .get(JobPropertyDefinitions.K8S_ADDITIONAL_JAVA_OPTS)
                        .value();
        return new V1EnvVar().name("ADDITIONAL_JAVA_OPTS").value(additionalJavaOpts);
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
        final var ttlAfterFinished =
                kubernetesJobRunContext
                        .jobRunContext()
                        .properties()
                        .get(JobPropertyDefinitions.K8S_TTL_AFTER_FINISHED)
                        .value();

        return new V1JobSpec()
                .ttlSecondsAfterFinished((int) ttlAfterFinished.toSeconds())
                .template(new V1PodTemplateSpec().spec(podSpec(kubernetesJobRunContext)));
    }

    protected String jobName(final KubernetesJobRunContext kubernetesJobRunContext) {
        return applicationContext.getEnvironment().getProperty("spring.application.name")
                + "-"
                + kubernetesJobRunContext.jobRunContext().job().name().toLowerCase()
                + "-run-"
                + kubernetesJobRunContext.jobRunContext().jobRun().id();
    }

    protected V1PodSpec podSpec(final KubernetesJobRunContext kubernetesJobRunContext) {
        final var serviceAccountName =
                kubernetesJobRunContext
                        .jobRunContext()
                        .properties()
                        .get(JobPropertyDefinitions.K8S_SERVICE_ACCOUNT_NAME)
                        .value();

        final var podSpec =
                new V1PodSpec()
                        .serviceAccountName(serviceAccountName)
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
        return getCurrentPodSpec(kubernetesJobRunContext)
                .map(V1PodSpec::getImagePullSecrets)
                .orElse(fallBackImagePullSecret());
    }

    private List<V1LocalObjectReference> fallBackImagePullSecret() {
        logger.info("Could not get image pull secret from current pod. Using fallback.");
        final var envKey = KubernetesEnvironmentVariables.K8S_IMAGE_PULL_SECRET.name();
        final var envImagePullSecret = System.getenv(envKey);
        if (StringUtils.isBlank(envImagePullSecret)) {
            logger.warn("Environment variable {} not set. No pull secret will be used.", envKey);
            return List.of();
        }
        return List.of(new V1LocalObjectReference().name(envImagePullSecret));
    }

    protected List<V1Volume> volumes(final KubernetesJobRunContext kubernetesJobRunContext) {
        return getCurrentPodSpec(kubernetesJobRunContext)
                .map(V1PodSpec::getVolumes)
                .orElse(List.of());
    }

    protected Map<String, String> nodeSelector(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        return getCurrentPodSpec(kubernetesJobRunContext)
                .map(V1PodSpec::getNodeSelector)
                .orElse(Map.of());
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
                kubernetesJobRunContext
                        .jobRunContext()
                        .properties()
                        .get(JobPropertyDefinitions.K8S_REQUESTS_CPU)
                        .valueMaybe()
                        .map(Quantity::fromString)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "must specify K8S_REQUESTS_CPU for a dedicated KubernetesJob"));
        final var memoryRequests =
                kubernetesJobRunContext
                        .jobRunContext()
                        .properties()
                        .get(JobPropertyDefinitions.K8S_REQUESTS_MEMORY)
                        .valueMaybe()
                        .map(Quantity::fromString)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "must specify K8S_REQUESTS_MEMORY for a dedicated KubernetesJob"));

        final var requests =
                Map.ofEntries(Map.entry("cpu", cpuRequests), Map.entry("memory", memoryRequests));
        final var limits = Map.ofEntries(Map.entry("memory", memoryRequests));
        return new V1ResourceRequirements().requests(requests).limits(limits);
    }
}
