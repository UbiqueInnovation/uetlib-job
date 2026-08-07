package ch.ubique.backend.job.service.executor;

import static ch.ubique.backend.job.model.properties.JobPropertyDefinitions.CLAIM_TIMEOUT;
import static ch.ubique.backend.job.model.properties.JobPropertyDefinitions.HEARTBEAT_TIMEOUT;

import ch.ubique.backend.core.utils.MapCollectors;
import ch.ubique.backend.core.utils.SingleCache;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobQueuedRun;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.queue.JobRunQueueState;
import ch.ubique.backend.job.service.JobService;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keeps {@code t_job_run_queue} honest once a row has been claimed:
 *
 * <ul>
 *   <li>a row stuck in {@link JobRunQueueState#CLAIMED} (the claiming instance died before ever
 *       submitting, so it never reached {@link JobRunQueueState#RUNNING}) is reset back to {@link
 *       JobRunQueueState#QUEUED} for another instance to retry
 *   <li>a {@link JobRunQueueState#RUNNING} row whose linked job run has actually finished is
 *       removed; one whose heartbeat has gone stale - the executing runner (see {@code
 *       JobRunnerHelper}) is no longer proving itself alive - is treated as abandoned: the job run
 *       is marked {@link JobRunState#ERROR} and the row is removed
 * </ul>
 *
 * Deliberately does not duplicate max-duration-based timeout detection - that's already handled at
 * the job run level (e.g. {@code JobTimeoutWatcher}). This is a liveness check: it fires regardless
 * of configured duration limits, as soon as the runner stops proving it's alive.
 */
@Service
@Profile("!job-run")
public class JobQueueWatcherService {

    private static final Logger logger = LoggerFactory.getLogger(JobQueueWatcherService.class);

    private final JobService jobService;
    private final JobDataService jobDataService;
    private final Duration defaultClaimTimeout;
    private final Duration defaultHeartbeatTimeout;

    private final SingleCache<Map<Integer, Duration>> jobClaimTimeoutCache;
    private final SingleCache<Map<Integer, Duration>> jobHeartbeatTimeoutCache;

    public JobQueueWatcherService(
            final JobService jobService,
            final JobDataService jobDataService,
            @Value("${ch.ubique.backend.job.runner.claim.timeout:2m}")
                    final Duration defaultClaimTimeout,
            @Value("${ch.ubique.backend.job.runner.heartbeat.timeout:1m}")
                    final Duration defaultHeartbeatTimeout) {
        this.jobService = jobService;
        this.jobDataService = jobDataService;
        this.defaultClaimTimeout = defaultClaimTimeout;
        this.defaultHeartbeatTimeout = defaultHeartbeatTimeout;
        this.jobClaimTimeoutCache =
                new SingleCache<>(
                        Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).build());
        this.jobHeartbeatTimeoutCache =
                new SingleCache<>(
                        Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).build());
    }

    /**
     * Per-job {@link ch.ubique.backend.job.model.properties.JobPropertyDefinitions#CLAIM_TIMEOUT}
     * overrides, cached for 30s - not computed once at startup, since an operator can change this
     * property live via the job properties API and this watcher should pick that up without
     * requiring a restart.
     */
    private Map<Integer, Duration> jobClaimTimeout() {
        return jobClaimTimeoutCache.get(
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
                                                                .get(CLAIM_TIMEOUT)
                                                                .valueMaybe()
                                                                .orElse(defaultClaimTimeout))));
    }

    /**
     * Per-job {@link ch.ubique.backend.job.model.properties.JobPropertyDefinitions#HEARTBEAT_TIMEOUT}
     * overrides, cached the same way as {@link #jobClaimTimeout()}.
     */
    private Map<Integer, Duration> jobHeartbeatTimeout() {
        return jobHeartbeatTimeoutCache.get(
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
                                                                .get(HEARTBEAT_TIMEOUT)
                                                                .valueMaybe()
                                                                .orElse(defaultHeartbeatTimeout))));
    }

    @Scheduled(cron = "${ch.ubique.backend.job.runner.reset-abandoned-claims.cron:14/15 * * * * ?}")
    public void resetAbandonedClaims() {
        final var staleBeforeByJobId =
                jobClaimTimeout().entrySet().stream()
                        .collect(MapCollectors.entriesToMapWithValueMapper(Instant.now()::minus));
        final var reset = jobDataService.resetStaleClaims(staleBeforeByJobId);
        if (reset > 0) {
            logger.info("Reset {} abandoned claim(s) back to queued.", reset);
        }
    }

    @Scheduled(cron = "${ch.ubique.backend.job.runner.reconcile-running.cron:13/15 * * * * ?}")
    public void reconcileRunningQueuedRuns() {
        final var runningQueuedRuns =
                jobDataService.findQueuedRunsByState(JobRunQueueState.RUNNING);
        runningQueuedRuns.forEach(this::reconcile);
    }

    private void reconcile(final JobQueuedRun queuedRun) {
        final var jobRunId = queuedRun.jobRunId();
        if (jobRunId == null) {
            // Should not happen - RUNNING is only ever set together with jobRunId. Defensive only.
            logger.warn(
                    "Queued run {} is RUNNING without a linked job run; resetting to queued.",
                    queuedRun.jobQueuedRunId());
            jobDataService.resetQueuedRunById(queuedRun.jobQueuedRunId());
            return;
        }

        final var jobRun = jobDataService.findJobRunById(jobRunId);
        if (jobRun.isEmpty() || !jobRun.get().latestState().isRunning()) {
            jobDataService.deleteQueuedRunById(queuedRun.jobQueuedRunId());
            return;
        }

        if (isHeartbeatStale(queuedRun)) {
            abandon(queuedRun, jobRun.get());
        }
        // Otherwise: leave the heartbeat alone. Only the runner actually executing the job (see
        // JobRunnerHelper) touches it now - refreshing it here too would defeat the point, since a
        // dead runner's row would then look alive forever.
    }

    private boolean isHeartbeatStale(final JobQueuedRun queuedRun) {
        final var timeout =
                jobHeartbeatTimeout().getOrDefault(queuedRun.jobId(), defaultHeartbeatTimeout);
        final var lastHeartbeat = queuedRun.lastHeartbeat();
        return lastHeartbeat == null || lastHeartbeat.isBefore(Instant.now().minus(timeout));
    }

    private void abandon(final JobQueuedRun queuedRun, final JobRun jobRun) {
        logger.warn(
                "Queued run {} has a stale heartbeat; its runner appears to be dead or stuck."
                        + " Marking job run {} as failed.",
                queuedRun,
                jobRun);
        jobService.updateJobRunState(jobRun, JobRunState.ERROR, Instant.now());
        jobDataService.deleteQueuedRunById(queuedRun.jobQueuedRunId());
    }
}
