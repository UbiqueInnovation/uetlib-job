package ch.ubique.uetlib.job.service.k8s;

import static ch.ubique.uetlib.job.k8s.KubernetesEnvironmentVariables.K8S_NAMESPACE;
import static ch.ubique.uetlib.job.k8s.KubernetesEnvironmentVariables.K8S_POD_NAME;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;

import ch.ubique.uetlib.job.model.JobRunState;
import ch.ubique.uetlib.job.model.k8s.KubernetesContext;
import ch.ubique.uetlib.spring.boot.utils.retry.LoggingRetryListener;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Namespaces;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.UncheckedException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;

@Service
@Profile("!job-run")
@Conditional(KubernetesAvailableConfigurationCondition.class)
public class KubernetesService {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesService.class);

    private static final RetryTemplate DEFAULT_RETRY_TEMPLATE_WITHOUT_LISTENER =
            new RetryTemplate(
                    RetryPolicy.builder()
                            .includes(ApiException.class)
                            .maxRetries(9)
                            .delay(Duration.ofSeconds(2))
                            .maxDelay(Duration.ofMinutes(5))
                            .multiplier(2)
                            .jitter(Duration.ofSeconds(1))
                            .build());

    private final ApiClient apiClient;
    private final String currentNamespace;
    private final @Nullable String currentPodName;

    private final RetryTemplate retryTemplate;

    public KubernetesService(final KubernetesClientProvider kubernetesClientProvider) {
        currentNamespace = getCurrentKubernetesNamespace();
        currentPodName = getCurrentPodName();
        apiClient = kubernetesClientProvider.getDefaultApiClient();
        warnIfNamespaceOrPodUnknown();

        this.retryTemplate = DEFAULT_RETRY_TEMPLATE_WITHOUT_LISTENER;
        retryTemplate.setRetryListener(new LoggingRetryListener());
    }

    public ApiClient apiClient() {
        return requireNonNull(apiClient, "ApiClient not available.");
    }

    public @Nullable V1Pod getCurrentPod() throws ApiException {
        if (StringUtils.isBlank(currentPodName) || StringUtils.isBlank(currentNamespace)) {
            logger.warn(
                    "Unable to get current pod, as the current pod name and/or namespace are"
                            + " unknown.");
            return null;
        }
        logger.info("Getting info for {} on namespace {}", currentPodName, currentNamespace);
        final var coreApi = new CoreV1Api(apiClient);

        try {
            return retryTemplate.execute(
                    () -> coreApi.readNamespacedPod(currentPodName, currentNamespace).execute());
        } catch (final RetryException e) {
            logger.warn("Unable to get current pod even after retries and backoff:", e);
            if (e.getCause() instanceof final ApiException apiException) {
                throw apiException;
            }
            if (e.getCause() instanceof final RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new UncheckedException(e.getCause());
        }
    }

    public KubernetesContext kubernetesContext() throws ApiException {
        final var currentPod = getCurrentPod();
        return new KubernetesContext(apiClient, currentNamespace, currentPod);
    }

    public String currentNamespace() {
        return currentNamespace;
    }

    public JobRunState getJobRunStatus(final V1Job job) {
        final var jobMetadata = requireNonNull(job.getMetadata());
        final var jobName = requireNonNull(jobMetadata.getName());
        final var status = requireNonNull(job.getStatus(), "Kubernetes job has no status.");
        logger.debug("Job {} has status {}", jobName, status);

        final var failedCount = requireNonNullElse(status.getFailed(), 0);
        final var succeededCount = requireNonNullElse(status.getSucceeded(), 0);
        final var maxRetries = ofNullable(job.getSpec()).map(V1JobSpec::getBackoffLimit).orElse(0);

        if (succeededCount > 0) {
            logger.debug("Job {} has succeeded. Set to DONE", jobName);
            return JobRunState.DONE;
        }
        if (failedCount > maxRetries) {
            logger.info("Job {} failed after {} retries. Set to ERROR", jobName, maxRetries);
            return JobRunState.ERROR;
        }
        if (status.getStartTime() == null) {
            logger.debug("Job {} has no start time. Set to SCHEDULED", jobName);
            return JobRunState.SCHEDULED;
        }

        logger.debug("Job {} is still running. Set to BUSY", jobName);
        return JobRunState.BUSY;
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

    private @Nullable String getCurrentPodName() {
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
