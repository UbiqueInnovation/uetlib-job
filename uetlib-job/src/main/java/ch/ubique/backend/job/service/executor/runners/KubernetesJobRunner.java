package ch.ubique.backend.job.service.executor.runners;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.k8s.KubernetesJob;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.k8s.KubernetesContext;
import ch.ubique.backend.job.model.k8s.KubernetesJobRunContext;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Namespaces;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class KubernetesJobRunner implements JobRunner {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ApplicationContext applicationContext;

    private final String namespace;
    private final String currentPodName;

    public KubernetesJobRunner(final ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        namespace = getCurrentKubernetesNamespace();
        currentPodName = System.getenv("HOSTNAME");
    }

    @Override
    public JobRunnerKey key() {
        return JobRunnerKey.DEDICATED_K8S_JOB;
    }

    @Override
    public boolean isAvailable() {
        if (StringUtils.isBlank(namespace)) {
            logger.warn(
                    "Could not get kubernetes namespace. If you are running the job on your local machine, this is the expected behaviour.");
            return false;
        }

        // Get current pod name
        if (StringUtils.isBlank(currentPodName)) {
            logger.warn(
                    "Could not get pod name. Running pod in the current context. If you are running"
                            + " the job on your local machine, this is the expected behaviour.");
            return false;
        }

        return true;
    }

    @Override
    public boolean submit(final Job job, final JobRunContext context) {
        final var client = tryToGetKubernetesApiClient();
        if (client == null) {
            return false;
        }

        try {
            final var currentPod = getCurrentPod(client);

            final var k8sContext =
                    new KubernetesJobRunContext(
                            context, new KubernetesContext(client, namespace, currentPod));
            final var jobDefinition = KubernetesJob.forJob(job, applicationContext).job(k8sContext);

            submitJobToKubernetes(client, jobDefinition);
            return true;

        } catch (final ApiException e) {
            logger.error("k8s exception: {}", e.getResponseBody(), e);
            return false;
        }
    }

    private ApiClient tryToGetKubernetesApiClient() {
        try {
            logger.info("Connecting to Kubernetes API");
            return Config.defaultClient();
        } catch (final IOException e) {
            logger.error("Could not connect to k8s", e);
            return null;
        }
    }

    private static String getCurrentKubernetesNamespace() {
        try {
            return Namespaces.getPodNamespace();
        } catch (final IOException e) {
            return System.getenv("K8S_NAMESPACE");
        }
    }

    private V1Pod getCurrentPod(final ApiClient client) throws ApiException {
        logger.info("Getting info for {} on namespace {}", currentPodName, namespace);
        final var coreApi = new CoreV1Api(client);
        return coreApi.readNamespacedPod(currentPodName, namespace, null);
    }

    private void submitJobToKubernetes(final ApiClient client, final V1Job jobDefinition)
            throws ApiException {
        final var batchApi = new BatchV1Api(client);
        final var createdJob =
                batchApi.createNamespacedJob(namespace, jobDefinition, null, null, null, null);

        logger.info(
                "Successfully created kubernetes job {}",
                Objects.requireNonNull(createdJob.getMetadata()).getName());
    }
}
