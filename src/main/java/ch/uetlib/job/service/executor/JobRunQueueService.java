package ch.uetlib.job.service.executor;

import static java.util.Objects.requireNonNull;

import ch.uetlib.core.utils.SingleCache;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.executer.SuccessfulSubmissionResult;
import ch.uetlib.job.model.executer.UnsuccessfulSubmissionResult;
import ch.uetlib.job.model.properties.JobPropertyDefinitions;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.model.runner.conditions.JobRunnerTaint;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.info.JobRunnerInfoService;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@Profile("!job-run")
public class JobRunQueueService {
    private static final Logger logger = LoggerFactory.getLogger(JobRunQueueService.class);

    public static final String RUNNER_INSTANCE_NAME_PROP =
            "ch.uetlib.job.runner.instance.name";

    /**
     * The name this instance claims queue rows under: {@value #RUNNER_INSTANCE_NAME_PROP} if set,
     * else the {@code HOSTNAME} env var (the pod name on Kubernetes), else the local host name.
     */
    public static String resolveInstanceName(final @Nullable String configuredInstanceName) {
        if (configuredInstanceName != null && !configuredInstanceName.isBlank()) {
            return configuredInstanceName;
        }
        final var hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (final UnknownHostException e) {
            throw new IllegalStateException(
                    "Cannot determine the job runner instance name: set "
                            + RUNNER_INSTANCE_NAME_PROP
                            + " or the HOSTNAME environment variable.",
                    e);
        }
    }

    private final JobService jobService;
    private final JobRunnerInfoService jobRunnerServiceInfo;
    private final JobExecutorService jobExecutorService;

    private final String instanceName;
    private final Set<String> instanceTaints;
    private final int maxRunnablePerCheck;

    private final AtomicBoolean checkForRunnableInProgress = new AtomicBoolean(false);
    private final SingleCache<Map<Integer, JobRunnerKey>> jobRunnerKeyCache;

    public JobRunQueueService(
            final JobService jobService,
            final JobRunnerInfoService jobRunnerServiceInfo,
            final JobExecutorService jobExecutorService,
            @Value("${" + RUNNER_INSTANCE_NAME_PROP + ":#{null}}")
                    final @Nullable String configuredInstanceName,
            @Value("${ch.uetlib.job.runner.instance.taints:null}")
                    final @Nullable List<String> instanceTaints,
            @Value("${ch.uetlib.job.runner.max.runnable.per.check:8}")
                    final int maxRunnablePerCheck) {
        this.jobService = jobService;
        this.jobRunnerServiceInfo = jobRunnerServiceInfo;
        this.jobExecutorService = jobExecutorService;
        this.instanceName = resolveInstanceName(configuredInstanceName);
        this.instanceTaints = Set.copyOf(Optional.ofNullable(instanceTaints).orElse(List.of()));
        this.maxRunnablePerCheck = maxRunnablePerCheck;
        this.jobRunnerKeyCache =
                new SingleCache<>(
                        Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).build());
    }

    @Scheduled(cron = "*/5 * * * * ?")
    public void checkForRunnableAndSchedule() {
        runExclusively(
                checkForRunnableInProgress, "checkForRunnableAndSchedule", this::processQueue);
    }

    /**
     * Skips {@code task} instead of running it concurrently with itself: {@code @Scheduled(cron =
     * ...)} fires on a fixed schedule regardless of whether the previous invocation has finished,
     * so a slow poll (or a scheduler with more than one thread) could otherwise overlap with
     * itself.
     */
    private void runExclusively(
            final AtomicBoolean inProgress, final String taskName, final Runnable task) {
        if (!inProgress.compareAndSet(false, true)) {
            logger.trace("Skipping {} - previous invocation is still running.", taskName);
            return;
        }
        try {
            task.run();
        } finally {
            inProgress.set(false);
        }
    }

    /**
     * Pages through queued runs, highest priority and oldest first, claiming/executing every row on
     * a page that matches this instance (plain or dedicated - see {@link
     * #claimIfMatchesThisRunner}) before considering the next page. Keeps fetching further pages
     * only while a page had no match at all, so a queue whose front this instance can't run doesn't
     * starve out matching rows further back; as soon as a page has any match, this stops for now -
     * the next tick (or a NOTIFY-triggered {@link #checkForRunnable}) picks up anything left.
     */
    private void processQueue() {
        var pageNumber = 0;
        while (true) {
            final var page =
                    jobService.findQueuedRunsPage(PageRequest.of(pageNumber, maxRunnablePerCheck));
            var anyMatch = false;
            for (final var jobQueuedRun : page.getContent()) {
                anyMatch |= claimIfMatchesThisRunner(jobQueuedRun);
            }
            if (anyMatch) {
                return;
            }
            if (!page.hasNext()) {
                logger.trace(
                        "No more job runs to run on this runner (instanceName={}, instanceTaints={})",
                        instanceName,
                        instanceTaints);
                return;
            }
            pageNumber++;
        }
    }

    /**
     * Fast path for {@link PgQueueInsertionListenService}: instead of re-running the full paged
     * poll, fetches exactly the notified rows and claims/executes only those that actually match
     * this instance's taints. Newly-inserted rows this instance doesn't match are ignored here -
     * they're still picked up normally by {@link #checkForRunnableAndSchedule} on whichever
     * instance they do match.
     */
    public void checkForRunnable(final List<Pair<Integer, Integer>> jobIdToQueueRunId) {
        final var newInserts =
                jobService.findQueuedByIds(jobIdToQueueRunId.stream().map(Pair::getRight).toList());
        newInserts.forEach(this::claimIfMatchesThisRunner);
    }

    /**
     * Claims and executes {@code jobQueuedRun} if it matches this instance.
     *
     * <p>Requires an exact {@code versionTag} match ({@link JobService#versionTag}) - not just
     * taints - before considering a claim at all. During a staggered/rolling release, instances
     * running different versions can briefly coexist; a run enqueued by one version must only ever
     * be executed by that same version, since an older instance may not know a newly-added job
     * class at all, and a newer instance may run different (possibly incompatible) code for it than
     * whatever enqueued the run intended.
     *
     * <p>Which of the two claim paths even applies is decided by the job's own {@code RUNNER}
     * property, not by whether {@code jobQueuedRun}'s taints happen to include {@link
     * JobRunnerTaints#DEDICATED_K8S_TAINT} - taints are an orthogonal, general-purpose mechanism
     * for restricting *which* instances (within a runner kind) may claim a run, not for choosing
     * the runner kind itself. A job with {@code RUNNER == DEDICATED_K8S_JOB} but no taints of its
     * own would otherwise trivially satisfy the plain path too (the empty set is a subset of
     * anything), letting it run on a plain {@code CurrentJobRunner} instance instead of being
     * dispatched to K8s.
     *
     * @return whether it matched, regardless of whether the subsequent claim attempt actually
     *     succeeded (it may lose a race to another instance, or a plain match may be skipped for
     *     lack of capacity)
     */
    private boolean claimIfMatchesThisRunner(final JobQueuedRun jobQueuedRun) {
        if (jobQueuedRun.queueState() != JobRunQueueState.QUEUED) {
            return false;
        }
        if (!jobQueuedRun.versionTag().equals(jobService.versionTag())) {
            return false;
        }
        if (jobRunnerKeys().get(jobQueuedRun.jobId()) == JobRunnerKey.DEDICATED_K8S_JOB) {
            if (matchesTaints(jobQueuedRun, getDedicatedTaints())) {
                claimAndExecuteDedicatedQueuedRun(jobQueuedRun);
                return true;
            }
            return false;
        }
        if (matchesTaints(jobQueuedRun, instanceTaints)) {
            claimAndExecuteQueuedRun(jobQueuedRun);
            return true;
        }
        return false;
    }

    /**
     * Per-job {@link JobPropertyDefinitions#RUNNER} overrides, cached for 30s - not computed once
     * at startup, since an operator can change this property live via the job properties API and
     * this poller should pick that up without requiring a restart.
     */
    private Map<Integer, JobRunnerKey> jobRunnerKeys() {
        return jobRunnerKeyCache.get(
                () ->
                        jobService.getAllJobs().stream()
                                .collect(
                                        Collectors.toMap(
                                                job ->
                                                        jobService
                                                                .findJobId(job.getClass())
                                                                .orElseThrow(),
                                                job ->
                                                        jobService
                                                                .getProperties(job)
                                                                .get(JobPropertyDefinitions.RUNNER)
                                                                .valueNonNull())));
    }

    /** The single place taint subset-matching (row's taints ⊆ this instance's) */
    private boolean matchesTaints(final JobQueuedRun jobQueuedRun) {
        return instanceTaints.containsAll(
                jobQueuedRun.taints().stream()
                        .map(JobRunnerTaint::value)
                        .collect(Collectors.toSet()));
    }

    private void claimAndExecuteQueuedRun(final JobQueuedRun jobQueuedRun) {
        claimAndExecuteQueuedRun(jobQueuedRun, false);
    }

    private void claimAndExecuteDedicatedQueuedRun(final JobQueuedRun jobQueuedRun) {
        if (!jobService.hasRunner(JobRunnerKey.DEDICATED_K8S_JOB)) {
            logger.info(
                    "Cannot execute {} here, because there is no dedicated runner type for {} available.",
                    JobRunnerKey.DEDICATED_K8S_JOB,
                    jobQueuedRun);
            return;
        }
        claimAndExecuteQueuedRun(jobQueuedRun, true);
    }

    private void claimAndExecuteQueuedRun(
            final JobQueuedRun jobQueuedRun, final boolean isDedicated) {
        final var jobClass = jobService.findJobById(jobQueuedRun.jobId()).orElseThrow();
        if (!isDedicated && !hasCapacity(jobClass)) {
            return;
        }
        if (!jobService.claimQueuedRun(jobQueuedRun, instanceName)) {
            logger.debug(
                    "Failed to claim queued run {}, appears to already have been claimed.",
                    jobQueuedRun);
            return;
        }
        switch (jobExecutorService.submit(jobQueuedRun, isDedicated)) {
            case SuccessfulSubmissionResult(_, _, final var runId) ->
                    jobService.setRunId(jobQueuedRun, runId);
            case UnsuccessfulSubmissionResult(final var status) -> {
                jobService.releaseQueuedRun(jobQueuedRun);
                switch (status) {
                    case SUBMITTED ->
                            logger.warn(
                                    "UnsuccessfulSubmissionResult should not receive submitted, but has been released again.");
                    case DISALLOWED ->
                            logger.warn(
                                    "Failed to submit job with status: {}, released for retry.",
                                    status);
                    case FAILED -> logger.info("Failed to submit job, released for retry.");
                }
            }
        }
    }

    private boolean hasCapacity(final JobClassAndId jobClass) {
        final var runnerGroup =
                requireNonNull(
                        jobService
                                .getProperties(jobClass)
                                .get(JobPropertyDefinitions.RUNNER_GROUP)
                                .value());
        return jobRunnerServiceInfo.getRunnerGroupsInfo().stream()
                .filter(g -> g.identifier().equals(runnerGroup.identifier()))
                .findAny()
                .map(g -> g.busyThreads() < g.currentPoolSize())
                .orElse(true);
    }
}
