package ch.ubique.backend.job.service.k8s;

import static ch.ubique.backend.job.k8s.KubernetesEnvironmentVariables.K8S_NAMESPACE;
import static ch.ubique.backend.job.k8s.KubernetesEnvironmentVariables.K8S_POD_NAME;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;

import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.k8s.KubernetesContext;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Namespaces;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Profile("!job-run")
@Conditional(KubernetesAvailableConfigurationCondition.class)
public class KubernetesService {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesService.class);

    private final ApiClient apiClient;
    private final String currentNamespace;
    private final String currentPodName;

    public KubernetesService() {
        currentNamespace = getCurrentKubernetesNamespace();
        currentPodName = getCurrentPodName();
        apiClient = getDefaultApiClient();
        warnIfNamespaceOrPodUnknown();
    }

    public ApiClient apiClient() {
        return requireNonNull(apiClient, "ApiClient not available.");
    }

    @Retryable(
            retryFor = ApiException.class,
            maxAttempts = 10,
            backoff = @Backoff(maxDelay = 300_000, multiplier = 2.0),
            listeners = "defaultRetryListener")
    public V1Pod getCurrentPod() throws ApiException {
        if (StringUtils.isBlank(currentPodName) || StringUtils.isBlank(currentNamespace)) {
            logger.warn(
                    "Unable to get current pod, as the current pod name and/or namespace are"
                            + " unknown.");
            return null;
        }
        logger.info("Getting info for {} on namespace {}", currentPodName, currentNamespace);
        final var coreApi = new CoreV1Api(apiClient);
        return coreApi.readNamespacedPod(currentPodName, currentNamespace).execute();
    }

    public KubernetesContext kubernetesContext() throws ApiException {
        final var currentPod = getCurrentPod();
        return new KubernetesContext(apiClient, currentNamespace, currentPod);
    }

    public String currentNamespace() {
        return currentNamespace;
    }

    public @NotNull JobRunState getJobRunStatus(final V1Job job) {
        final var jobMetadata = requireNonNull(job.getMetadata());
        final var jobName = requireNonNull(jobMetadata.getName());
        final var status = requireNonNull(job.getStatus(), "Kubernetes job has no status.");
        logger.debug("Job {} has status {}", jobName, status);

        final var failedCount = requireNonNullElse(status.getFailed(), 0);
        final var succeededCount = requireNonNullElse(status.getSucceeded(), 0);
        final var maxRetries = ofNullable(job.getSpec()).map(V1JobSpec::getBackoffLimit).orElse(0);

        if (succeededCount > 0) {
            return JobRunState.DONE;
        }
        if (failedCount > maxRetries) {
            return JobRunState.ERROR;
        }
        if (status.getStartTime() == null) {
            return JobRunState.SCHEDULED;
        }
        return JobRunState.BUSY;
    }

    private static ApiClient getDefaultApiClient() {
        try {
            return Config.defaultClient();
        } catch (final IOException e) {
            logger.warn("Could not connect to k8s", e);
            return null;
        }
    }

    private String getCurrentKubernetesNamespace() {
        try {
            return Namespaces.getPodNamespace();
        } catch (final IOException e) {
            final var namespaceFromEnv = System.getenv(K8S_NAMESPACE.name());
            if (StringUtils.isNotBlank(namespaceFromEnv)) {
                return namespaceFromEnv;
            }
            logger.warn("{} environment variable not set. Using default namespace", K8S_NAMESPACE);
            return "default";
        }
    }

    private String getCurrentPodName() {
        final var podNameFromEnvVar = System.getenv(K8S_POD_NAME.name());
        if (StringUtils.isNotBlank(podNameFromEnvVar)) {
            logger.info("Used pod name set by environment variable {}", K8S_POD_NAME);
            return podNameFromEnvVar;
        }

        final var hostname = System.getenv("HOSTNAME");
        if (StringUtils.isNotBlank(hostname)) {
            logger.info("Assuming hostname {} is the name of the current pod", hostname);
            return hostname;
        }

        logger.warn("Unable to find current pod name.");
        return null;
    }

    private void warnIfNamespaceOrPodUnknown() {
        if (StringUtils.isBlank(currentNamespace)) {
            logger.warn(
                    "Could not get kubernetes namespace. If this application is not running the on"
                            + " a k8s cluster, this is the expected behaviour.");
        }

        // Get current pod name
        if (StringUtils.isBlank(currentPodName)) {
            logger.warn(
                    "Could not get pod name. If this application is not running the on a k8s"
                            + " cluster, this is the expected behaviour.");
        }
    }
}
