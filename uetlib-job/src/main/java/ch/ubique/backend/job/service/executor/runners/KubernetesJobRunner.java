package ch.ubique.backend.job.service.executor.runners;

import static java.util.Objects.requireNonNull;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.k8s.KubernetesEnvironmentVariables;
import ch.ubique.backend.job.k8s.KubernetesJob;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.k8s.KubernetesContext;
import ch.ubique.backend.job.model.k8s.KubernetesJobRunContext;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.JobService;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Namespaces;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Profile("!job-run")
public class KubernetesJobRunner implements JobRunner {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesJobRunner.class);

    private final ApplicationContext applicationContext;
    private final JobDataService jobDataService;

    private final ExecutorService jobWatcherExecutor;

    private final String namespace;
    private final String currentPodName;

    public KubernetesJobRunner(final ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        namespace = getCurrentKubernetesNamespace();
        currentPodName = getCurrentPodName();
        jobDataService = applicationContext.getBean(JobDataService.class);

        jobWatcherExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    private static String getCurrentKubernetesNamespace() {
        try {
            return Namespaces.getPodNamespace();
        } catch (final IOException e) {
            final var namespaceFromEnv =
                    System.getenv(KubernetesEnvironmentVariables.K8S_NAMESPACE.name());
            if (StringUtils.isNotBlank(namespaceFromEnv)) {
                return namespaceFromEnv;
            }
            logger.warn(
                    "{} environment variable not set. Using default namespace",
                    KubernetesEnvironmentVariables.K8S_NAMESPACE);
            return "default";
        }
    }

    private static String getCurrentPodName() {
        final var podNameFromEnvVar =
                System.getenv(KubernetesEnvironmentVariables.K8S_POD_NAME.name());
        if (StringUtils.isNotBlank(podNameFromEnvVar)) {
            logger.info(
                    "Used pod name set by environment variable {}",
                    KubernetesEnvironmentVariables.K8S_POD_NAME);
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

    @Override
    public @NotNull JobRunnerKey key() {
        return JobRunnerKey.DEDICATED_K8S_JOB;
    }

    @Override
    public boolean submit(final @NotNull Job job, final @NotNull JobRunContext jobRunContext) {
        final var kubernetesJob = KubernetesJob.forJob(job, applicationContext);
        final var client = tryToGetKubernetesApiClient(kubernetesJob);
        if (client == null) {
            return false;
        }

        try {
            final var kubernetesJobRunContext =
                    new KubernetesJobRunContext(jobRunContext, kubernetesContext(client));
            final var jobDefinition = kubernetesJob.job(kubernetesJobRunContext);

            final var jobRunId = jobRunContext.jobRun().id();
            final var k8sJobName = submitJobToKubernetes(client, jobDefinition, jobRunId);
            jobDataService.saveK8sJob(jobRunId, k8sJobName);
            return true;

        } catch (final ApiException e) {
            logger.error("k8s exception: {}", e.getResponseBody(), e);
            return false;
        }
    }

    @Override
    public void cancel(final @NotNull JobRun jobRun) {
        final var k8sJobNameMaybe = jobDataService.findK8sJobName(jobRun.id());
        if (k8sJobNameMaybe.isEmpty()) {
            logger.info("No k8s job found for given job run.");
            return;
        }

        try {
            final var client = getClient(jobRun);
            final var jobName = k8sJobNameMaybe.get();
            final var batchApi = new BatchV1Api(client);
            logger.info("Deleting {} for namespace {} using batch API", jobName, namespace);
            final var status =
                    batchApi.deleteNamespacedJob(jobName, namespace)
                            .propagationPolicy("Foreground")
                            .execute();
            logger.info("Completed delete request with status {}", status);
        } catch (final IOException | ClassNotFoundException | ApiException e) {
            logger.info("Got exception when trying to cancel job run {}", jobRun, e);
        }
    }

    @Override
    public boolean isRunning(final @NotNull JobRun jobRun) {
        final var k8sJobNameMaybe = jobDataService.findK8sJobName(jobRun.id());
        if (k8sJobNameMaybe.isEmpty()) {
            return false;
        }

        final var jobName = k8sJobNameMaybe.get();

        try {
            final var client = getClient(jobRun);
            final var batchApi = new BatchV1Api(client);
            return batchApi.listNamespacedJob(namespace).execute().getItems().stream()
                    .filter(job -> jobName.equals(jobName(job)))
                    .findFirst()
                    .map(V1Job::getStatus)
                    .map(V1JobStatus::getActive)
                    .map(active -> active > 0)
                    .orElse(false);
        } catch (final IOException | ClassNotFoundException | ApiException e) {
            logger.warn(
                    "Got exception when trying to query the status of {}. Treating as running.",
                    jobName,
                    e);
            return true;
        }
    }

    @Nullable
    private static String jobName(final V1Job job) {
        return Optional.ofNullable(job)
                .map(V1Job::getMetadata)
                .map(V1ObjectMeta::getName)
                .orElse(null);
    }

    private ApiClient getClient(final JobRun jobRun) throws ClassNotFoundException, IOException {
        final var jobService = applicationContext.getBean(JobService.class);
        final var job = jobService.instantiateJob(jobRun.jobId());
        return KubernetesJob.forJob(job, applicationContext).apiClient();
    }

    private ApiClient tryToGetKubernetesApiClient(final KubernetesJob kubernetesJob) {
        warnIfNamespaceOrPodUnknown();

        try {
            logger.info("Connecting to Kubernetes API");
            return kubernetesJob.apiClient();
        } catch (final IOException e) {
            logger.error("Could not connect to k8s", e);
            return null;
        }
    }

    private void warnIfNamespaceOrPodUnknown() {
        if (StringUtils.isBlank(namespace)) {
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

    // min delay 2 seconds, max delay 5 minutes with random delays
    @Retryable(
            retryFor = ApiException.class,
            maxAttempts = 10,
            backoff = @Backoff(maxDelay = 300_000, multiplier = 2.0),
            listeners = "defaultRetryListener")
    private V1Pod getCurrentPod(final ApiClient client) throws ApiException {
        if (StringUtils.isBlank(currentPodName) || StringUtils.isBlank(namespace)) {
            logger.warn(
                    "Unable to get current pod, as the current pod name and/or namespace are"
                            + " unknown.");
            return null;
        }
        logger.info("Getting info for {} on namespace {}", currentPodName, namespace);
        final var coreApi = new CoreV1Api(client);
        return coreApi.readNamespacedPod(currentPodName, namespace).execute();
    }

    private KubernetesContext kubernetesContext(final ApiClient client) throws ApiException {
        final var currentPod = getCurrentPod(client);
        return new KubernetesContext(client, namespace, currentPod);
    }

    // min delay 2 seconds, max delay 5 minutes with random delays
    @Retryable(
            retryFor = ApiException.class,
            maxAttempts = 10,
            backoff = @Backoff(maxDelay = 300_000, multiplier = 2.0),
            listeners = "defaultRetryListener")
    private String submitJobToKubernetes(
            final ApiClient client, final V1Job jobDefinition, final int jobRunId)
            throws ApiException {
        final var batchApi = new BatchV1Api(client);
        final var createdJob = batchApi.createNamespacedJob(namespace, jobDefinition).execute();

        final var k8sJobName = requireNonNull(createdJob.getMetadata()).getName();
        logger.info("Successfully created kubernetes job {}", k8sJobName);

        jobWatcherExecutor.submit(new KubernetesJobWatcher(client, createdJob, jobRunId));

        return k8sJobName;
    }

    private class KubernetesJobWatcher implements Callable<KubernetesJobStatus> {
        private ApiClient client;
        private V1Job createdJob;
        private int jobRunId;

        public KubernetesJobWatcher(
                final ApiClient client, final V1Job createdJob, final int jobRunId) {
            this.client = client;
            this.createdJob = createdJob;
            this.jobRunId = jobRunId;
        }

        @Override
        public KubernetesJobStatus call() throws ApiException, InterruptedException {
            final var createdJobName =
                    requireNonNull(requireNonNull(createdJob.getMetadata()).getName());
            final var api = new BatchV1Api(client);
            while (true) {
                Thread.sleep(10_000);
                final var jobList = api.listNamespacedJob(namespace).execute();
                final var jobFromApiOptional =
                        jobList.getItems().stream()
                                .filter(
                                        job ->
                                                job.getMetadata() != null
                                                        && createdJobName.equals(
                                                                job.getMetadata().getName()))
                                .findAny();
                final var status =
                        jobFromApiOptional
                                .map(
                                        jobFromApi ->
                                                KubernetesJobStatus.fromStatus(
                                                        requireNonNull(jobFromApi.getStatus())))
                                .orElse(KubernetesJobStatus.UNKNOWN);
                if (status == KubernetesJobStatus.ACTIVE) {
                    continue;
                }
                jobDataService.finishJobRun(jobRunId, status.jobRunState(), Instant.now());
                switch (status) {
                    case SUCCEEDED -> logger.info("Job Run {} succeeded.", jobRunId);
                    case FAILED -> logger.error("Job Run {} failed.", jobRunId);
                    case UNKNOWN -> logger.warn("Job Run {} not found in K8S.", jobRunId);
                }
                return status;
            }
        }
    }

    private enum KubernetesJobStatus {
        ACTIVE(null),
        SUCCEEDED(JobRunState.DONE),
        FAILED(JobRunState.ERROR),
        UNKNOWN(JobRunState.UNFINISHED);

        private final JobRunState jobRunState;

        KubernetesJobStatus(final JobRunState jobRunState) {
            this.jobRunState = jobRunState;
        }

        public static KubernetesJobStatus fromStatus(final V1JobStatus status) {
            if (requireNonNull(status.getActive()) != 0) return ACTIVE;
            if (requireNonNull(status.getSucceeded()) == 1) return SUCCEEDED;
            if (requireNonNull(status.getFailed()) == 1) return FAILED;
            return UNKNOWN;
        }

        public JobRunState jobRunState() {
            if (this == ACTIVE) {
                throw new UnsupportedOperationException();
            }
            return jobRunState;
        }
    }
}
