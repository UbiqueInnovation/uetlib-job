package ch.uetlib.job.service.watcher;

import static ch.uetlib.core.utils.MdcUtils.runnableWithContext;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.service.executor.heartbeat.HeartbeatRegistry;
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
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * How often log activity is allowed to touch the heartbeat (a chatty job shouldn't turn into a
     * write on every single line), and - doubling as the same threshold - how long logs may stay
     * quiet before {@link #startQuietPodWatchdog} falls back to an active Kubernetes-side liveness
     * check instead of trusting log activity alone.
     */
    private static final Duration LOG_ACTIVITY_INTERVAL = Duration.ofSeconds(15);

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final KubernetesService kubernetesService;
    private final ExecutorService executorService;
    private final JobDataService jobDataService;
    private final HeartbeatRegistry heartbeatRegistry;

    public KubernetesJobWatcher(
            final KubernetesService kubernetesService,
            final JobDataService jobDataService,
            final HeartbeatRegistry heartbeatRegistry) {
        this.kubernetesService = kubernetesService;
        this.jobDataService = jobDataService;
        this.heartbeatRegistry = heartbeatRegistry;
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
                jobDataService.touchQueuedRunHeartbeatByJobRunId(jobRun.id());
                for (final var pod : pods) {
                    final var uid = requireNonNull(requireNonNull(pod.getMetadata()).getUid());
                    if (!watchedPods.contains(uid)) {
                        watchedPods.add(uid);
                        final var mdc = MDC.getCopyOfContextMap();
                        executorService.execute(
                                runnableWithContext(
                                        () -> {
                                            try {
                                                watchPodLogs(pod, jobRun.id());
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

    private void watchPodLogs(final V1Pod pod, final int jobRunId) {
        final var metadata = requireNonNull(pod.getMetadata());
        final var podName = requireNonNull(metadata.getName());
        final var namespace = requireNonNull(metadata.getNamespace());
        waitForPodReady(namespace, podName);

        logger.info("Watching logs for pod {}", podName);
        final var lastLogActivityAt = new AtomicReference<>(Instant.now());
        final var watchdog = startQuietPodWatchdog(namespace, podName, jobRunId, lastLogActivityAt);
        try (final var response =
                        new CoreV1Api(kubernetesService.apiClient())
                                .readNamespacedPodLog(podName, namespace)
                                .follow(true)
                                .buildCall(null)
                                .execute();
                final var charStream = requireNonNull(response.body()).charStream();
                final var reader = new LineWithEndingReader(new BufferedReader(charStream))) {
            readLogLines(reader, jobRunId, lastLogActivityAt);
        } catch (final IOException e) {
            logger.info("Exception while logs job.", e);
        } catch (final ApiException e) {
            logger.info("Exception while logs job: {}", e.getResponseBody(), e);
        } finally {
            // false, not true: this task only ever does a quick DB write or K8s API call, never
            // anything worth interrupting mid-flight.
            watchdog.cancel(false);
        }
    }

    /**
     * Each real log line is itself proof this pod is making progress, not just still around - a
     * stronger signal than {@link #watchJobState}'s per-event touch alone, which only proves
     * Kubernetes hasn't reported the job as failed. Debounced to {@link #LOG_ACTIVITY_INTERVAL} so
     * a chatty job doesn't turn into a heartbeat write on every single line; {@code
     * lastLogActivityAt} is still updated on every line so {@link #startQuietPodWatchdog} knows
     * logging hasn't gone quiet even between debounced touches.
     */
    private void readLogLines(
            final LineWithEndingReader reader,
            final int jobRunId,
            final AtomicReference<Instant> lastLogActivityAt)
            throws IOException {
        long lastPrintedLogMillis = -1;
        var lastHeartbeatTouchAt = Instant.EPOCH;
        final var printRateLimit = PROGRESS_RATE_LIMIT.toMillis();
        LineWithEnding line;
        while ((line = reader.readLineWithEnding()) != null) {
            if (!isNotBlank(line.text())) {
                continue;
            }
            final var now = Instant.now();
            lastLogActivityAt.set(now);

            final var currentTimeMillis = System.currentTimeMillis();
            if (line.isNewLine() || currentTimeMillis - lastPrintedLogMillis >= printRateLimit) {
                logger.info("Pod logs: {}", line.text());
                lastPrintedLogMillis = currentTimeMillis;
            }

            if (Duration.between(lastHeartbeatTouchAt, now).compareTo(LOG_ACTIVITY_INTERVAL) >= 0) {
                jobDataService.touchQueuedRunHeartbeatByJobRunId(jobRunId);
                lastHeartbeatTouchAt = now;
            }
        }
    }

    /**
     * Falls back to an active Kubernetes-side liveness check once a pod's logs have been quiet for
     * {@link #LOG_ACTIVITY_INTERVAL} - a legitimately busy job might just not be logging anything
     * for a while, but this at least confirms the pod is still there and hasn't restarted since we
     * started watching it, rather than blindly trusting silence.
     */
    private ScheduledFuture<?> startQuietPodWatchdog(
            final String namespace,
            final String podName,
            final int jobRunId,
            final AtomicReference<Instant> lastLogActivityAt) {
        final var baselineRestartCount = totalRestartCount(namespace, podName).orElse(0);
        return heartbeatRegistry.scheduleWithFixedDelay(
                () ->
                        checkQuietPod(
                                namespace,
                                podName,
                                jobRunId,
                                lastLogActivityAt,
                                baselineRestartCount),
                LOG_ACTIVITY_INTERVAL.toSeconds(),
                LOG_ACTIVITY_INTERVAL.toSeconds(),
                TimeUnit.SECONDS);
    }

    private void checkQuietPod(
            final String namespace,
            final String podName,
            final int jobRunId,
            final AtomicReference<Instant> lastLogActivityAt,
            final int baselineRestartCount) {
        final var timeDiff = Duration.between(lastLogActivityAt.get(), Instant.now());
        if (timeDiff.compareTo(LOG_ACTIVITY_INTERVAL) < 0) {
            return; // logs are still flowing; readLogLines already covers this
        }

        final var restartCount = totalRestartCount(namespace, podName);
        if (restartCount.isEmpty()) {
            logger.warn(
                    "Pod {} could not be found while checking for quiet-log liveness.", podName);
            return;
        }
        if (restartCount.get() > baselineRestartCount) {
            logger.warn(
                    "Pod {} has restarted (restart count {} > baseline {}) while its logs were"
                            + " quiet; not touching its heartbeat.",
                    podName,
                    restartCount.get(),
                    baselineRestartCount);
            return;
        }

        logger.debug("Pod {}'s logs are quiet, but it's still up with no new restarts.", podName);
        jobDataService.touchQueuedRunHeartbeatByJobRunId(jobRunId);
    }

    private Optional<Integer> totalRestartCount(final String namespace, final String podName) {
        try {
            final var pod =
                    new CoreV1Api(kubernetesService.apiClient())
                            .readNamespacedPod(podName, namespace)
                            .execute();
            final var containerStatuses =
                    ofNullable(pod.getStatus())
                            .map(V1PodStatus::getContainerStatuses)
                            .orElse(List.of());
            if (containerStatuses.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(
                    containerStatuses.stream().mapToInt(V1ContainerStatus::getRestartCount).sum());
        } catch (final ApiException e) {
            logger.info("Could not read pod {} status: {}", podName, e.getResponseBody(), e);
            return Optional.empty();
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
