package ch.uetlib.job.service.executor;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import ch.uetlib.core.utils.SingleCache;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.JobRun;
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
import java.time.Instant;
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
    private final int maxPagesPerCheck;
    private final Duration unknownJobTimeout;

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
                    final int maxRunnablePerCheck,
            @Value("${ch.uetlib.job.runner.max.pages.per.check:5}")
                    final int maxPagesPerCheck,
            @Value("${ch.uetlib.job.runner.unknown-job.timeout:PT5M}")
                    final Duration unknownJobTimeout) {
        this.jobService = jobService;
        this.jobRunnerServiceInfo = jobRunnerServiceInfo;
        this.jobExecutorService = jobExecutorService;
        this.instanceName = resolveInstanceName(configuredInstanceName);
        this.instanceTaints = Set.copyOf(Optional.ofNullable(instanceTaints).orElse(List.of()));
        this.dedicatedInstanceTaints = getDedicatedTaints();
        this.maxRunnablePerCheck = maxRunnablePerCheck;
        this.maxPagesPerCheck = maxPagesPerCheck;
        this.unknownJobTimeout = unknownJobTimeout;
        this.jobRunnerKeyCache =
                new SingleCache<>(
                        Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).build());
    }

    @Scheduled(cron = "${ch.uetlib.job.runner.check-for-runnable.cron:*/5 * * * * ?}")
    public void checkForRunnableAndSchedule() {
        runExclusively(
                checkForRunnableInProgress, "checkForRunnableAndSchedule", this::processQueue);
    }

    /**
     * Skips {@code task} instead of running it concurrently with itself: {@code @Scheduled(cron =
     * ...)} fires on a fixed schedule regardless of whether the previous invocation has finished,
     * so a slow poll (or a scheduler with more than one thread) could otherwise overlap with
     * itself.
     *
     * <p>This guards the scheduled poll only - {@link #checkForRunnable} runs the same claim path
     * from the {@code NOTIFY} listener thread without it, deliberately, so a notification is never
     * dropped just because a poll is in flight. The two overlapping is safe: claiming is a
     * conditional {@code UPDATE ... WHERE queue_state = QUEUED} whose row count decides the winner,
     * so the loser simply moves on.
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
     * a page this instance can take (plain or dedicated - see {@link #claimIfMatchesThisRunner})
     * before considering the next page. Stops as soon as a page yielded a claim, leaving the rest
     * to the next tick (or a NOTIFY-triggered {@link #checkForRunnable}).
     *
     * <p>Only <em>taking</em> a row counts. A row this instance matches but cannot take right now -
     * its runner group is at capacity, or another instance won the claim - is not progress, so the
     * scan moves on rather than stopping: one saturated runner group at the front of the queue must
     * not block runs for idle groups behind it. {@code maxPagesPerCheck} bounds how far that can
     * go, so a queue this instance can take nothing from costs a bounded number of queries.
     */
    private void processQueue() {
        for (var pageNumber = 0; pageNumber < maxPagesPerCheck; pageNumber++) {
            final var page =
                    jobService.findQueuedRunsPage(PageRequest.of(pageNumber, maxRunnablePerCheck));
            var progressed = false;
            for (final var jobQueuedRun : page.getContent()) {
                progressed |= claimIfMatchesThisRunnerIsolated(jobQueuedRun);
            }
            if (progressed) {
                return;
            }
            if (!page.hasNext()) {
                logger.trace(
                        "No more job runs to run on this runner (instanceName={}, instanceTaints={})",
                        instanceName,
                        instanceTaints);
                return;
            }
        }
        logger.debug(
                "Stopped after {} pages without taking a run; the rest waits for the next check.",
                maxPagesPerCheck);
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
        newInserts.forEach(this::claimIfMatchesThisRunnerIsolated);
    }

    /**
     * {@link #claimIfMatchesThisRunner}, with one row's failure contained to that row, so a single
     * unprocessable row at the front of the queue cannot block everything behind it on every tick.
     */
    private boolean claimIfMatchesThisRunnerIsolated(final JobQueuedRun jobQueuedRun) {
        try {
            return claimIfMatchesThisRunner(jobQueuedRun);
        } catch (final Exception e) {
            logger.error(
                    "Failed to process queued run {}; skipping it and continuing with the rest of"
                            + " the queue.",
                    jobQueuedRun,
                    e);
            return false;
        }
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
     * <p>Which of the two claim paths applies is decided by {@link #runnerKindFor}: the job's
     * {@code RUNNER} property normally, or - for a {@link ch.uetlib.job.BaseStateJob}
     * handoff row, bound to an existing run - that run's own {@code runnerKey}. Not by {@code
     * jobQueuedRun}'s taints: taints are an orthogonal mechanism for restricting <em>which</em>
     * instances (within a runner kind) may claim a run, not for choosing the runner kind itself.
     *
     * @return whether this instance actually took the row. A match that could not be taken - no
     *     capacity, or a claim lost to another instance - returns {@code false}, so the scan keeps
     *     looking behind it.
     */
    private boolean claimIfMatchesThisRunner(final JobQueuedRun jobQueuedRun) {
        if (jobQueuedRun.queueState() != JobRunQueueState.QUEUED) {
            return false;
        }
        if (!jobQueuedRun.versionTag().equals(jobService.versionTag())) {
            return false;
        }
        if (jobService.findJobById(jobQueuedRun.jobId()).isEmpty()) {
            cancelIfUnknownForTooLong(jobQueuedRun);
            return false;
        }
        if (!matchesTaints(jobQueuedRun)) {
            return false;
        }
        if (runnerKindFor(jobQueuedRun) == JobRunnerKey.DEDICATED_K8S_JOB) {
            return claimAndExecuteDedicatedQueuedRun(jobQueuedRun);
        }
        return claimAndExecuteQueuedRun(jobQueuedRun);
    }

    /**
     * Gives up on a row whose job this build doesn't have - the version check already passed, so
     * nothing allowed to claim it could resolve the job either. Cancelled, not deleted.
     *
     * <p>Not on sight, though: an instance builds its job registry once at startup, so during a
     * rollout an instance about to be replaced can briefly not know a job a newer one just
     * registered. Waiting out {@code unknownJobTimeout} - long enough for a rollout, far shorter
     * than {@code QUEUE_TIMEOUT} - lets that instance go away rather than cancel a run its
     * replacement could have executed.
     */
    private void cancelIfUnknownForTooLong(final JobQueuedRun jobQueuedRun) {
        final var unknownFor = Duration.between(jobQueuedRun.updatedAt(), Instant.now());
        if (unknownFor.compareTo(unknownJobTimeout) < 0) {
            logger.debug(
                    "Job id {} of queued run {} is unknown to this build; leaving it for now ({} of"
                            + " {} elapsed).",
                    jobQueuedRun.jobId(),
                    jobQueuedRun.jobQueuedRunId(),
                    unknownFor,
                    unknownJobTimeout);
            return;
        }
        logger.error(
                "Cancelling queued run {}: job id {} has been unknown to this build ({}) for {}, so"
                        + " nothing allowed to claim this row can execute it.",
                jobQueuedRun.jobQueuedRunId(),
                jobQueuedRun.jobId(),
                jobQueuedRun.versionTag(),
                unknownFor);
        if (!jobService.cancelUnclaimableQueuedRun(jobQueuedRun)) {
            logger.info("Failed to cancel unknown job {}.", jobQueuedRun);
        }
    }

    /**
     * The runner kind a queued row must go to: the bound run's {@code runnerKey} for a handoff row
     * (a {@code QUEUED} row that already has a {@code jobRunId} - a {@link
     * ch.uetlib.job.BaseStateJob} continuation whose processing phase may target a
     * different runner than the job's {@code RUNNER} property), else that property.
     */
    private JobRunnerKey runnerKindFor(final JobQueuedRun jobQueuedRun) {
        final var runId = jobQueuedRun.jobRunId();
        if (runId != null) {
            return jobService
                    .getJobRunMaybe(runId)
                    .map(JobRun::runnerKey)
                    .orElseGet(() -> configuredRunnerKey(jobQueuedRun.jobId()));
        }
        return configuredRunnerKey(jobQueuedRun.jobId());
    }

    /**
     * The job's configured {@link JobPropertyDefinitions#RUNNER}, falling back to {@link
     * JobRunnerKey#CURRENT} for a job id no longer in the cache (e.g. its class was removed since
     * the row was enqueued) - such a row then takes the plain path and fails to resolve later.
     */
    private JobRunnerKey configuredRunnerKey(final int jobId) {
        return requireNonNullElse(jobRunnerKeys().get(jobId), JobRunnerKey.CURRENT);
    }

    /**
     * Per-job {@link JobPropertyDefinitions#RUNNER} overrides, cached for 30s - not computed once
     * at startup, since an operator can change this property live via the job properties API and
     * this poller should pick that up without requiring a restart.
     */
    private Map<Integer, JobRunnerKey> jobRunnerKeys() {
        return jobRunnerKeyCache.get(
                () -> jobService.propertyValueByJobId(JobPropertyDefinitions.RUNNER));
    }

    /** The single place taint subset-matching (row's taints ⊆ this instance's) */
    private boolean matchesTaints(final JobQueuedRun jobQueuedRun) {
        return instanceTaints.containsAll(
                jobQueuedRun.taints().stream()
                        .map(JobRunnerTaint::value)
                        .collect(Collectors.toSet()));
    }

    private boolean claimAndExecuteQueuedRun(final JobQueuedRun jobQueuedRun) {
        return claimAndExecuteQueuedRun(jobQueuedRun, false);
    }

    private boolean claimAndExecuteDedicatedQueuedRun(final JobQueuedRun jobQueuedRun) {
        if (!jobService.hasRunner(JobRunnerKey.DEDICATED_K8S_JOB)) {
            logger.info(
                    "Cannot execute {} here, because there is no dedicated runner type for {} available.",
                    JobRunnerKey.DEDICATED_K8S_JOB,
                    jobQueuedRun);
            return false;
        }
        return claimAndExecuteQueuedRun(jobQueuedRun, true);
    }

    /**
     * @return whether this instance claimed the row; see {@link #claimIfMatchesThisRunner}.
     */
    private boolean claimAndExecuteQueuedRun(
            final JobQueuedRun jobQueuedRun, final boolean isDedicated) {
        final var jobClass = jobService.findJobById(jobQueuedRun.jobId()).orElseThrow();
        if (!isDedicated && !hasCapacity(jobClass)) {
            return false;
        }
        if (!jobService.claimQueuedRun(jobQueuedRun, instanceName)) {
            logger.debug(
                    "Failed to claim queued run {}, appears to already have been claimed.",
                    jobQueuedRun);
            return false;
        }
        switch (jobExecutorService.submit(jobQueuedRun, isDedicated)) {
            // Already linked and marked RUNNING by JobExecutorService#submit, before the runner
            // was handed the job - doing it here would race the job's own execution.
            case SuccessfulSubmissionResult _ -> logger.debug("Dispatched {}.", jobQueuedRun);
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
        // Claimed either way: the row was ours this tick, so the scan stops here even if the
        // submission then failed and released it for a retry.
        return true;
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
