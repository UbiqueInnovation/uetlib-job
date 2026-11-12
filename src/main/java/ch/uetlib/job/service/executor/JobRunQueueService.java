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
import ch.uetlib.job.service.executor.runners.CurrentJobRunner;
import ch.uetlib.job.service.instance.JobInstanceIdentity;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@Profile("!job-run")
public class JobRunQueueService {
    private static final Logger logger = LoggerFactory.getLogger(JobRunQueueService.class);

    /**
     * How often a row this instance waits to run is marked as still wanted: often enough for {@code
     * QUEUE_TIMEOUT} never to cancel it meanwhile, rarely enough not to write on every check.
     */
    private static final Duration WAITING_TOUCH_INTERVAL = Duration.ofMinutes(1);

    private final JobService jobService;
    private final CurrentJobRunner currentJobRunner;
    private final JobExecutorService jobExecutorService;

    private final String daemonName;
    private final Set<String> instanceTaints;
    private final int maxRunnablePerCheck;
    private final int maxPagesPerCheck;
    private final Duration unknownJobTimeout;

    private final AtomicBoolean checkForRunnableInProgress = new AtomicBoolean(false);
    private final Lock dispatchLock = new ReentrantLock();
    private final SingleCache<Map<Integer, JobRunnerKey>> jobRunnerKeyCache;

    public JobRunQueueService(
            final JobService jobService,
            final CurrentJobRunner currentJobRunner,
            final JobExecutorService jobExecutorService,
            final JobInstanceIdentity instanceIdentity,
            @Value("${ch.uetlib.job.runner.max.runnable.per.check:8}")
                    final int maxRunnablePerCheck,
            @Value("${ch.uetlib.job.runner.max.pages.per.check:5}") final int maxPagesPerCheck,
            @Value("${ch.uetlib.job.runner.unknown-job.timeout:PT5M}")
                    final Duration unknownJobTimeout) {
        this.jobService = jobService;
        this.currentJobRunner = currentJobRunner;
        this.jobExecutorService = jobExecutorService;
        this.daemonName = instanceIdentity.name();
        this.instanceTaints = instanceIdentity.taints();
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
     * Skips {@code task} while its previous run is still going. The NOTIFY path bypasses this on
     * purpose; it takes turns with the poll only per row, in {@link #claimAndExecuteQueuedRun}.
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
     * Claims what this instance can take, page by page, until a page yields a claim; matches it
     * can't take don't stop the scan.
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
                        "No more job runs to run on this runner (daemonName={}, instanceTaints={})",
                        daemonName,
                        instanceTaints);
                return;
            }
        }
        logger.debug(
                "Stopped after {} pages without taking a run; the rest waits for the next check.",
                maxPagesPerCheck);
    }

    /**
     * NOTIFY fast path: claims just the notified rows this instance matches; the poll picks up the
     * rest.
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
     * Claims and executes the row if it has this exact version and its taints fit this instance for
     * its runner kind ({@link #runnerKindFor}). Returns whether the row was actually taken.
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
     * Cancels a row whose job this build lacks, but only after {@code unknownJobTimeout}, so a
     * rollout can finish first. Not an error: {@code JobQueueCancelledRunsCheck} reports the row.
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
        logger.info(
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
     * The bound run's {@code runnerKey} for a {@code BaseStateJob} handoff row, else the job's
     * {@code RUNNER}.
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

    /** The job's {@code RUNNER}, or {@code CURRENT} for a job id no longer known. */
    private JobRunnerKey configuredRunnerKey(final int jobId) {
        return requireNonNullElse(jobRunnerKeys().get(jobId), JobRunnerKey.CURRENT);
    }

    /** Per-job {@code RUNNER}, cached for 30s so live property changes apply without a restart. */
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
            logger.debug(
                    "Cannot execute {} here, because there is no dedicated runner type for {} available.",
                    JobRunnerKey.DEDICATED_K8S_JOB,
                    jobQueuedRun);
            return false;
        }
        return claimAndExecuteQueuedRun(jobQueuedRun, true);
    }

    /**
     * One row at a time per instance: the poll and the NOTIFY path must not both pass the checks of
     * {@link #claimIfStartable} for the last free thread, or for two runs of a job that disallows
     * parallel runs.
     */
    private boolean claimAndExecuteQueuedRun(
            final JobQueuedRun jobQueuedRun, final boolean isDedicated) {
        dispatchLock.lock();
        try {
            return claimIfStartable(jobQueuedRun, isDedicated);
        } finally {
            dispatchLock.unlock();
        }
    }

    /**
     * Claims the row only if it can start here right away: a free thread in its runner group (for
     * {@code CURRENT}), and {@link JobExecutorService#canStart}. Anything else would only bounce
     * back, so the row keeps waiting instead (see {@link #keepWaiting}).
     *
     * @return whether this instance claimed the row; see {@link #claimIfMatchesThisRunner}.
     */
    private boolean claimIfStartable(final JobQueuedRun jobQueuedRun, final boolean isDedicated) {
        final var jobClass = jobService.findJobById(jobQueuedRun.jobId()).orElseThrow();
        if ((!isDedicated && !hasCapacity(jobClass))
                || !jobExecutorService.canStart(jobQueuedRun)) {
            keepWaiting(jobQueuedRun);
            return false;
        }
        if (!jobService.claimQueuedRun(jobQueuedRun, daemonName)) {
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
                    // Another instance started the job, or it was disabled, since canStart.
                    case DISALLOWED ->
                            logger.debug(
                                    "Queued run {} may no longer start; released for later.",
                                    jobQueuedRun);
                    case FAILED -> logger.info("Failed to submit job, released for retry.");
                }
            }
        }
        // Claimed either way: the row was ours this tick, so the scan stops here even if the
        // submission then failed and released it for a retry.
        return true;
    }

    /** Whether the job's runner group has a free thread on this instance. */
    private boolean hasCapacity(final JobClassAndId jobClass) {
        final var runnerGroup =
                requireNonNull(
                        jobService
                                .getProperties(jobClass)
                                .get(JobPropertyDefinitions.RUNNER_GROUP)
                                .value());
        return currentJobRunner.hasCapacity(runnerGroup);
    }

    /**
     * Leaves a row this instance could run, just not yet, queued - but marks it as still wanted,
     * since {@code QUEUE_TIMEOUT} counts from a row's last activity and must not cancel a run that
     * only waits for a free thread or for its job's current run.
     */
    private void keepWaiting(final JobQueuedRun jobQueuedRun) {
        logger.trace("Queued run {} cannot start here yet; it keeps waiting.", jobQueuedRun);
        if (jobQueuedRun.updatedAt().isBefore(Instant.now().minus(WAITING_TOUCH_INTERVAL))) {
            jobService.touchWaitingQueuedRun(jobQueuedRun);
        }
    }
}
