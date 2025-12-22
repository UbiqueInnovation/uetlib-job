package ch.ubique.backend.job.service.executor.runners;

import static java.util.Objects.requireNonNull;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.k8s.KubernetesJob;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.k8s.KubernetesJobRunContext;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.k8s.KubernetesAvailableConfigurationCondition;
import ch.ubique.backend.job.service.k8s.KubernetesService;
import ch.ubique.backend.job.service.watcher.KubernetesJobWatcher;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.*;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Profile("!job-run")
@Conditional(KubernetesAvailableConfigurationCondition.class)
public class KubernetesJobRunner implements JobRunner {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesJobRunner.class);

    private final ApplicationContext applicationContext;
    private final JobDataService jobDataService;
    private final KubernetesService kubernetesService;
    private final KubernetesJobWatcher jobWatcher;

    public KubernetesJobRunner(final ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        kubernetesService = applicationContext.getBean(KubernetesService.class);
        jobDataService = applicationContext.getBean(JobDataService.class);
        jobWatcher = applicationContext.getBean(KubernetesJobWatcher.class);
    }

    @Override
    public @NotNull JobRunnerKey key() {
        return JobRunnerKey.DEDICATED_K8S_JOB;
    }

    @Override
    public boolean submit(final @NotNull Job job, final @NotNull JobRunContext jobRunContext) {
        final var kubernetesJob = KubernetesJob.forJob(job, applicationContext);
        try {
            final var kubernetesJobRunContext =
                    new KubernetesJobRunContext(
                            jobRunContext, kubernetesService.kubernetesContext());
            final var jobDefinition = kubernetesJob.job(kubernetesJobRunContext);
            final var k8sJobName = submitJobToKubernetes(jobDefinition);
            jobDataService.saveK8sJob(jobRunContext.jobRun().id(), k8sJobName);
            if (kubernetesJob.watchJob()) {
                jobWatcher.watch(k8sJobName, jobRunContext.jobRun());
            }
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
            final var client = kubernetesService.apiClient();
            final var namespace = kubernetesService.currentNamespace();
            final var jobName = k8sJobNameMaybe.get();
            final var batchApi = new BatchV1Api(client);
            logger.info("Deleting {} for namespace {} using batch API", jobName, namespace);
            final var status =
                    batchApi.deleteNamespacedJob(jobName, namespace)
                            .propagationPolicy("Foreground")
                            .execute();
            logger.info("Completed delete request with status {}", status);
        } catch (final ApiException e) {
            logger.info("Got exception when trying to cancel job run {}", jobRun, e);
        }
    }

    @Override
    public JobRunState resolveJobRunState(final @NotNull JobRun jobRun) {
        final var k8sJobNameMaybe = jobDataService.findK8sJobName(jobRun.id());
        if (k8sJobNameMaybe.isEmpty()) {
            return null;
        }

        final var jobName = k8sJobNameMaybe.get();

        try {
            final var client = kubernetesService.apiClient();
            final var namespace = kubernetesService.currentNamespace();
            final var batchApi = new BatchV1Api(client);
            return batchApi.listNamespacedJob(namespace).execute().getItems().stream()
                    .filter(job -> jobName.equals(jobName(job)))
                    .findFirst()
                    .map(kubernetesService::getJobRunStatus)
                    .orElse(null);
        } catch (final ApiException e) {
            logger.warn(
                    "Got exception when trying to query the status of {}. Treating as running (return null).",
                    jobName,
                    e);
            return JobRunState.BUSY;
        }
    }

    @Nullable
    private static String jobName(final V1Job job) {
        return Optional.ofNullable(job)
                .map(V1Job::getMetadata)
                .map(V1ObjectMeta::getName)
                .orElse(null);
    }

    // min delay 2 seconds, max delay 5 minutes with random delays
    @Retryable(
            retryFor = ApiException.class,
            maxAttempts = 10,
            backoff = @Backoff(maxDelay = 300_000, multiplier = 2.0),
            listeners = "defaultRetryListener")
    private String submitJobToKubernetes(final V1Job jobDefinition) throws ApiException {
        final var batchApi = new BatchV1Api(kubernetesService.apiClient());
        final var namespace = kubernetesService.currentNamespace();
        final var createdJob = batchApi.createNamespacedJob(namespace, jobDefinition).execute();

        final var k8sJobName = requireNonNull(createdJob.getMetadata()).getName();
        logger.info("Successfully created kubernetes job {}", k8sJobName);

        return k8sJobName;
    }
}
