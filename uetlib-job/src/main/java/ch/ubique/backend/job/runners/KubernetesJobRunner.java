package ch.ubique.backend.job.runners;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Namespaces;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class KubernetesJobRunner implements JobRunner {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Environment environment;

    public KubernetesJobRunner(final Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(final Job job) throws IOException, ApiException {
        logger.info("Connecting to Kubernetes API");
        final var client = Config.defaultClient();
        final var coreApi = new CoreV1Api(client);
        final var batchApi = new BatchV1Api(client);

        // Get namespace
        final var namespaceMaybe = getCurrentKubernetesNamespace();
        if (namespaceMaybe.isEmpty()) {
            logger.warn(
                    "Could not get kubernetes namespace. Running pod in the current context. If you"
                            + " are running the job on your local machine, this is the expected"
                            + " behaviour.");
            job.run(false);
            return;
        }

        // Get current pod name
        final var currentPodName = System.getenv("HOSTNAME");
        if (StringUtils.isBlank(currentPodName)) {
            logger.warn(
                    "Could not get pod name. Running pod in the current context. If you are running"
                            + " the job on your local machine, this is the expected behaviour.");
            job.run(false);
            return;
        }

        // Get current pod
        final var namespace = namespaceMaybe.get();
        logger.info("Getting info for {} on namespace {}", currentPodName, namespace);
        final var currentPod = coreApi.readNamespacedPod(currentPodName, namespace, null);

        // Create job
        final var jobName = jobName(job);
        final var kubernetesJob = createKubernetesJob(job, namespace, jobName, currentPod);
        batchApi.createNamespacedJob(namespace, kubernetesJob, null, null, null, null);

        logger.info("Successfully created kubernetes job {}", jobName);
    }

    private V1Job createKubernetesJob(
            final Job job,
            final String namespace,
            final String jobName,
            final V1Pod currentPod) {

        // Assume there is only one container
        final var currentPodSpec = Objects.requireNonNull(currentPod.getSpec());
        if (currentPodSpec.getContainers().size() != 1) {
            throw new IllegalArgumentException(
                    "Expected current importer pod to have exactly one container. This is a"
                            + " simplification of the current implementation. If needed, you can modify"
                            + " this code to drop this restriction.");
        }
        final var currentPodContainer = currentPodSpec.getContainers().get(0);

        final var args =
                new ArrayList<>(
                        List.of(
                                "--",
                                "--job",
                                job.getClass().getName(),
                                "--run",
                                String.valueOf(job.jobRun().getId())));
        if (job.isForceRunEnabled()) {
            args.add("--force");
        }

        final var cpuRequests =
                Quantity.fromString(
                        job.getProperty(JobPropertyDefinitions.K8S_REQUESTS_CPU).value());
        final var memoryRequests =
                Quantity.fromString(
                        job.getProperty(JobPropertyDefinitions.K8S_REQUESTS_MEMORY).value());

        final var requests =
                Map.ofEntries(Map.entry("cpu", cpuRequests), Map.entry("memory", memoryRequests));
        final var limits = Map.ofEntries(Map.entry("memory", memoryRequests));

        final var container =
                new V1Container()
                        .name("job-image")
                        .image(currentPodContainer.getImage())
                        .args(args)
                        .env(currentPodContainer.getEnv())
                        .addEnvItem(
                                new V1EnvVar().name("JAVA_OPTS").value("-XX:MaxRAMPercentage=95.0"))
                        .envFrom(currentPodContainer.getEnvFrom())
                        .volumeMounts(currentPodContainer.getVolumeMounts())
                        .imagePullPolicy(currentPodContainer.getImagePullPolicy())
                        .resources(new V1ResourceRequirements().requests(requests).limits(limits));

        final var podSpec =
                new V1PodSpec()
                        .addContainersItem(container)
                        .imagePullSecrets(currentPodSpec.getImagePullSecrets())
                        .volumes(currentPodSpec.getVolumes())
                        .nodeSelector(currentPodSpec.getNodeSelector())
                        .restartPolicy("Never");

        job.getProperty(JobPropertyDefinitions.K8S_SKU)
                .valueMaybe()
                .ifPresent(
                        value ->
                                podSpec.addTolerationsItem(
                                        new V1Toleration()
                                                .key("sku")
                                                .operator("Equal")
                                                .value(value)
                                                .effect("NoSchedule")));

        // Add annotations to make Prometheus scrape our job
        final var annotations =
                Map.ofEntries(
                        Map.entry("prometheus.io/path", "/actuator/prometheus"),
                        Map.entry("prometheus.io/port", "8085"),
                        Map.entry("prometheus.io/scrape", "true"));

        final var metadata =
                new V1ObjectMeta().namespace(namespace).name(jobName).annotations(annotations);
        final var ttl = job.getProperty(JobPropertyDefinitions.K8S_TTL_AFTER_FINISHED).value();

        final var jobSpec =
                new V1JobSpec()
                        .ttlSecondsAfterFinished((int) ttl.toSeconds())
                        .template(new V1PodTemplateSpec().spec(podSpec));

        return new V1Job().metadata(metadata).spec(jobSpec);
    }

    @NotNull
    private String jobName(final Job job) {
        return environment.getProperty("spring.application.name")
                + "-"
                + job.getClass().getSimpleName().toLowerCase()
                + "-run-"
                + job.jobRun().getId();
    }

    private Optional<String> getCurrentKubernetesNamespace() {
        try {
            return Optional.of(Namespaces.getPodNamespace());
        } catch (final IOException e) {
            return Optional.ofNullable(System.getenv("K8S_NAMESPACE"));
        }
    }
}
