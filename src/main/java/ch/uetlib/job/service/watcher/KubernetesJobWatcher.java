package ch.uetlib.job.service.watcher;

import static ch.uetlib.core.utils.MdcUtils.runnableWithContext;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.service.k8s.KubernetesAvailableConfigurationCondition;
import ch.uetlib.job.service.k8s.KubernetesService;
import ch.uetlib.job.service.watcher.LineWithEndingReader.LineWithEnding;

import com.google.common.reflect.TypeToken;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.BatchV1Api.APIlistNamespacedJobRequest;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watch.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Profile("!job-run")
@Conditional(KubernetesAvailableConfigurationCondition.class)
public class KubernetesJobWatcher {

    /**
     * Some pods might use \r to move the carriage back to the start of the current line when
     * logging progress. Without a rate limit, this results in way too many lines logged by this
     * service. Therefore, carriage return 'lines' are only printed according to this rate limit.
     * New lines (\n) are always printed.
     */
    private static final Duration PROGRESS_RATE_LIMIT = Duration.ofSeconds(5);

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final KubernetesService kubernetesService;
    private final ExecutorService executorService;
    private final JobDataService jobDataService;

    public KubernetesJobWatcher(
            final KubernetesService kubernetesService, final JobDataService jobDataService) {
        this.kubernetesService = kubernetesService;
        this.jobDataService = jobDataService;
        final var threadFactory = Thread.ofVirtual().name("k8s-watcher-", 0).factory();
        this.executorService = Executors.newThreadPerTaskExecutor(threadFactory);
    }

    public void watch(final String jobName, final JobRun jobRun) {
        executorService.execute(() -> watchJobState(jobName, jobRun));
    }

    private void watchJobState(final String jobName, final JobRun jobRun) {
        final var previousContextMap = MDC.getCopyOfContextMap();
        MDC.put("jobRunId", String.valueOf(jobRun.id()));
        JobRunState previousState = null;
        final var watchedPods = new ArrayList<String>();
        try (final var watch = createWatch(jobName)) {
            for (final var item : watch) {
                final var job = item.object;
                final var jobRunState = kubernetesService.getJobRunStatus(job);
                final var pods = getPods(jobName);
                if (jobRunState != previousState) {
                    logger.info("Observed {} is now in state {}", jobName, jobRunState);
                    jobDataService.updateJobRunState(jobRun.id(), jobRunState, Instant.now());
                    if (!jobRunState.isRunning()) {
                        break;
                    }
                    previousState = jobRunState;
                }
                for (final var pod : pods) {
                    final var uid = requireNonNull(requireNonNull(pod.getMetadata()).getUid());
                    if (!watchedPods.contains(uid)) {
                        watchedPods.add(uid);
                        final var mdc = MDC.getCopyOfContextMap();
                        executorService.execute(
                                runnableWithContext(
                                        () -> {
                                            try {
                                                watchPodLogs(pod);
                                            } catch (final RuntimeException e) {
                                                logger.error(
                                                        "Unexpected failure watching logs for pod {}",
                                                        uid,
                                                        e);
                                            }
                                        },
                                        mdc));
                    }
                }
            }
        } catch (final ApiException e) {
            logger.info("Could not create watch: {}", e.getResponseBody(), e);
        } catch (final IOException e) {
            logger.info("Exception while watching job.", e);
        } catch (final RuntimeException e) {
            logger.error("Unexpected failure watching job {}", jobName, e);
        } finally {
            MDC.setContextMap(previousContextMap);
        }
    }

    private void watchPodLogs(final V1Pod pod) {
        final var metadata = requireNonNull(pod.getMetadata());
        final var podName = requireNonNull(metadata.getName());
        final var namespace = requireNonNull(metadata.getNamespace());
        waitForPodReady(namespace, podName);

        logger.info("Watching logs for pod {}", podName);
        try (final var response =
                        new CoreV1Api(kubernetesService.apiClient())
                                .readNamespacedPodLog(podName, namespace)
                                .follow(true)
                                .buildCall(null)
                                .execute();
                final var charStream = requireNonNull(response.body()).charStream();
                final var reader = new LineWithEndingReader(new BufferedReader(charStream))) {
            readLogLines(reader);
        } catch (final IOException e) {
            logger.info("Exception while logs job.", e);
        } catch (final ApiException e) {
            logger.info("Exception while logs job: {}", e.getResponseBody(), e);
        }
    }

    private void readLogLines(final LineWithEndingReader reader) throws IOException {
        long lastLogMillis = -1;
        final var rateLimit = PROGRESS_RATE_LIMIT.toMillis();
        LineWithEnding line;
        while ((line = reader.readLineWithEnding()) != null) {
            final var currentTimeMillis = System.currentTimeMillis();
            final var millisSinceLast = currentTimeMillis - lastLogMillis;
            if (isNotBlank(line.text()) && (line.isNewLine() || millisSinceLast >= rateLimit)) {
                logger.info("Pod logs: {}", line.text());
                lastLogMillis = currentTimeMillis;
            }
        }
    }

    private void waitForPodReady(final String namespace, final String podName) {
        final var coreApi = new CoreV1Api(kubernetesService.apiClient());
        try (final Watch<V1Pod> watch =
                Watch.createWatch(
                        coreApi.getApiClient(),
                        coreApi.listNamespacedPod(namespace)
                                .fieldSelector("metadata.name=" + podName)
                                .watch(true)
                                .buildCall(null),
                        new TypeToken<Response<V1Pod>>() {}.getType())) {

            for (final var event : watch) {
                final var pod = event.object;
                final var containerStatuses =
                        ofNullable(pod.getStatus())
                                .map(V1PodStatus::getContainerStatuses)
                                .orElse(List.of());
                if (!containerStatuses.isEmpty()
                        && containerStatuses.stream().allMatch(V1ContainerStatus::getReady)) {
                    logger.debug("Containers of {} are ready.", podName);
                    return;
                }
            }
        } catch (final ApiException | IOException e) {
            logger.info("Exception while waiting for pod to be ready.", e);
        }
    }

    private Collection<V1Pod> getPods(final String jobName) throws ApiException {
        final var coreApi = new CoreV1Api(kubernetesService.apiClient());
        return coreApi.listNamespacedPod(kubernetesService.currentNamespace())
                .labelSelector("job-name=" + jobName)
                .execute()
                .getItems();
    }

    private Watch<V1Job> createWatch(final String jobName) throws ApiException {
        final var client = kubernetesService.apiClient();
        final var listJob = jobListRequest(jobName).watch(true).buildCall(null);
        return Watch.createWatch(client, listJob, new TypeToken<Response<V1Job>>() {}.getType());
    }

    private APIlistNamespacedJobRequest jobListRequest(final String jobName) {
        final var batchApi = new BatchV1Api(kubernetesService.apiClient());
        return batchApi.listNamespacedJob(kubernetesService.currentNamespace())
                .fieldSelector("metadata.name=" + jobName);
    }
}
