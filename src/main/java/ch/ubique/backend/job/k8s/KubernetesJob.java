package ch.ubique.backend.job.k8s;

import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.k8s.KubernetesJobRunContext;
import ch.ubique.backend.job.model.k8s.KubernetesPodToleration;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
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
        if (job instanceof final CustomKubernetesJobCreator customKubernetesJobCreator) {
            return customKubernetesJobCreator.createKubernetesJob(applicationContext);
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
        return new V1ObjectMeta()
                .namespace(namespace(kubernetesJobRunContext))
                .name(kubernetesJobName(kubernetesJobRunContext))
                .annotations(annotations(kubernetesJobRunContext))
                .labels(labels(kubernetesJobRunContext));
    }

    protected Optional<String> maybeGetCurrentPodName(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        return Optional.ofNullable(kubernetesJobRunContext.kubernetesContext().currentPod())
                .map(V1Pod::getMetadata)
                .map(V1ObjectMeta::getName);
    }

    protected String getCurrentPodName(final KubernetesJobRunContext kubernetesJobRunContext) {
        return maybeGetCurrentPodName(kubernetesJobRunContext)
                .orElseGet(
                        () -> {
                            final var springApplicationName =
                                    requireNonNull(
                                            applicationContext
                                                    .getEnvironment()
                                                    .getProperty("spring.application.name"));
                            logger.warn(
                                    "Could not get current pod name, using"
                                            + " spring.application.name={} as fallback",
                                    springApplicationName);
                            return springApplicationName;
                        });
    }

    protected String getCurrentDeploymentName(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        return maybeGetCurrentPodName(kubernetesJobRunContext)
                .map(
                        podName -> {
                            final var splitPodName = Arrays.stream(podName.split("-")).toList();
                            // the pod name always with '-[replica set id]-[pod id]'
                            return splitPodName.stream()
                                    .limit(splitPodName.size() - 2L)
                                    .collect(joining("-"));
                        })
                .orElseGet(
                        () -> {
                            final var springApplicationName =
                                    requireNonNull(
                                            applicationContext
                                                    .getEnvironment()
                                                    .getProperty("spring.application.name"));
                            logger.warn(
                                    "Could not get current deployment name, using"
                                            + " spring.application.name={} as fallback",
                                    springApplicationName);
                            return springApplicationName;
                        });
    }

    protected Map<String, String> annotations(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        final var currentPodName = getCurrentPodName(kubernetesJobRunContext);
        // Add annotations to make Prometheus scrape our job
        return ofEntries(
                entry("prometheus.io/path", "/actuator/prometheus"),
                entry("prometheus.io/port", "8085"),
                entry("prometheus.io/scrape", "true"),
                // for easy identification after the fact
                entry("backend.systems.ubique.ch/job-spawned-by-pod", currentPodName));
    }

    protected Map<String, String> labels(final KubernetesJobRunContext kubernetesJobRunContext) {
        final var labels = new HashMap<String, String>();
        labels.put("app", appName());
        labels.putAll(labelsFromProperties(kubernetesJobRunContext.jobRunContext()));
        return labels;
    }

    private String appName() {
        final var environment = applicationContext.getEnvironment();
        return requireNonNull(environment.getProperty("spring.application.name"));
    }

    private static Map<String, String> labelsFromProperties(final JobRunContext jobRunContext) {
        return jobRunContext.properties().get(JobPropertyDefinitions.K8S_POD_LABELS).value();
    }

    /**
     * Get pod tolerations from {@link JobPropertyDefinitions#K8S_POD_TOLERATIONS} or, if not set,
     * from the current pod
     */
    protected List<V1Toleration> tolerations(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        return kubernetesJobRunContext
                .jobRunContext()
                .properties()
                .get(JobPropertyDefinitions.K8S_POD_TOLERATIONS)
                .valueMaybe()
                .map(
                        kubernetesPodTolerations ->
                                kubernetesPodTolerations.tolerations().stream()
                                        .map(KubernetesPodToleration::toV1Toleration)
                                        .toList())
                .orElseGet(
                        () ->
                                // get node selector from current pod if not explicitly set
                                getCurrentPodSpec(kubernetesJobRunContext)
                                        .map(V1PodSpec::getTolerations)
                                        .orElseGet(List::of));
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
                .template(template(kubernetesJobRunContext));
    }

    protected V1PodTemplateSpec template(final KubernetesJobRunContext kubernetesJobRunContext) {
        final var template = new V1PodTemplateSpec().spec(podSpec(kubernetesJobRunContext));
        template.setMetadata(podTemplateMetadata(kubernetesJobRunContext));
        return template;
    }

    protected V1ObjectMeta podTemplateMetadata(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        return new V1ObjectMeta()
                .labels(labels(kubernetesJobRunContext))
                .annotations(annotations(kubernetesJobRunContext));
    }

    protected String kubernetesJobName(final KubernetesJobRunContext kubernetesJobRunContext) {
        final var currentDeploymentName = getCurrentDeploymentName(kubernetesJobRunContext);
        final var jobName = kubernetesJobRunContext.jobRunContext().job().name().toLowerCase();
        final var jobRunId = kubernetesJobRunContext.jobRunContext().jobRun().id();

        final var kubernetesJobName = currentDeploymentName + "-" + jobName + "-run-" + jobRunId;

        // see https://kubernetes.io/docs/concepts/workloads/controllers/job/#writing-a-job-spec
        if (kubernetesJobName.length() <= 63) {
            return kubernetesJobName;
        }

        final var shortKubernetesJobName = currentDeploymentName + "-run-" + jobRunId;
        if (shortKubernetesJobName.length() <= 63) {
            logger.info(
                    "job name cannot be longer than 63 characters, got {} characters for job name {}."
                            + " Using shortened name: {}",
                    kubernetesJobName.length(),
                    kubernetesJobName,
                    shortKubernetesJobName);
            return shortKubernetesJobName;
        }

        final var shortCurrentDeploymentName =
                Arrays.stream(currentDeploymentName.split("-"))
                        .filter(StringUtils::isNotBlank)
                        .map(part -> part.substring(0, 1))
                        .collect(joining("-"));
        final var evenShorterKubernetesJobName = shortCurrentDeploymentName + "-run-" + jobRunId;
        // Pray it is short enough
        if (evenShorterKubernetesJobName.length() <= 63) {
            logger.info(
                    "job name cannot be longer than 63 characters, got {} characters for job name {}."
                            + " Using shortened name: {}",
                    kubernetesJobName.length(),
                    kubernetesJobName,
                    evenShorterKubernetesJobName);
            return evenShorterKubernetesJobName;
        }

        // commit Seppuku
        throw new IllegalStateException(
                "Job name still longer than 63 characters even after shortening "
                        + kubernetesJobName
                        + " down to "
                        + evenShorterKubernetesJobName
                        + ". Cannot handle :(");
    }

    protected V1PodSpec podSpec(final KubernetesJobRunContext kubernetesJobRunContext) {
        final var podSpec =
                new V1PodSpec()
                        .serviceAccountName(serviceAccountName(kubernetesJobRunContext))
                        .imagePullSecrets(imagePullSecrets(kubernetesJobRunContext))
                        .volumes(volumes(kubernetesJobRunContext))
                        .nodeSelector(nodeSelector(kubernetesJobRunContext))
                        .tolerations(tolerations(kubernetesJobRunContext))
                        .affinity(affinity(kubernetesJobRunContext))
                        .restartPolicy("Never");

        containers(kubernetesJobRunContext).forEach(podSpec::addContainersItem);

        return podSpec;
    }

    protected String serviceAccountName(final KubernetesJobRunContext kubernetesJobRunContext) {
        return getCurrentPodSpec(kubernetesJobRunContext)
                .map(V1PodSpec::getServiceAccountName)
                // null is fine (locally when not running in k8s, will be set to default
                // serviceAccount by k8s
                .orElse(null);
    }

    protected List<V1LocalObjectReference> imagePullSecrets(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        return getCurrentPodSpec(kubernetesJobRunContext)
                .map(V1PodSpec::getImagePullSecrets)
                .orElseGet(this::fallBackImagePullSecret);
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
                .orElseGet(List::of);
    }

    /**
     * Get pod node selector from {@link JobPropertyDefinitions#K8S_POD_NODE_SELECTOR} or, if not
     * set, from the current pod
     */
    protected Map<String, String> nodeSelector(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        return kubernetesJobRunContext
                .jobRunContext()
                .properties()
                .get(JobPropertyDefinitions.K8S_POD_NODE_SELECTOR)
                .valueMaybe()
                .orElseGet(
                        () ->
                                // get node selector from current pod if not explicitly set
                                getCurrentPodSpec(kubernetesJobRunContext)
                                        .map(V1PodSpec::getNodeSelector)
                                        .orElseGet(Map::of));
    }

    protected abstract Collection<V1Container> containers(
            final KubernetesJobRunContext kubernetesJobRunContext);

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
                                                "must specify K8S_REQUESTS_CPU for a dedicated"
                                                        + " KubernetesJob"));
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
                                                "must specify K8S_REQUESTS_MEMORY for a dedicated"
                                                        + " KubernetesJob"));

        final var requests = ofEntries(entry("cpu", cpuRequests), entry("memory", memoryRequests));
        final var limits = ofEntries(entry("memory", memoryRequests));
        return new V1ResourceRequirements().requests(requests).limits(limits);
    }

    protected V1Affinity affinity(final KubernetesJobRunContext kubernetesJobRunContext) {
        return getCurrentPodSpec(kubernetesJobRunContext).map(V1PodSpec::getAffinity).orElse(null);
    }
}
