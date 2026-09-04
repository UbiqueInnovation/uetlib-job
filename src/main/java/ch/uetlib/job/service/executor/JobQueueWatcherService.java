package ch.uetlib.job.service.executor;

import static ch.uetlib.job.model.properties.JobPropertyDefinitions.CANCELLED_QUEUE_RETENTION;
import static ch.uetlib.job.model.properties.JobPropertyDefinitions.CLAIM_TIMEOUT;
import static ch.uetlib.job.model.properties.JobPropertyDefinitions.QUEUE_TIMEOUT;

import ch.uetlib.core.utils.MapCollectors;
import ch.uetlib.core.utils.SingleCache;
import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.service.JobService;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps {@code t_job_run_queue} honest once a row has been claimed - or never gets claimed at all:
 *
 * <ul>
 *   <li>a row stuck in {@link JobRunQueueState#CLAIMED} (the claiming instance died before ever
 *       submitting, so it never reached {@link JobRunQueueState#RUNNING}) is reset back to {@link
 *       JobRunQueueState#QUEUED} for another instance to retry
 *   <li>a {@link JobRunQueueState#RUNNING} row whose linked job run has actually finished (or
 *       vanished) is removed - the only path that deletes a {@code RUNNING} queue row
 *   <li>a {@link JobRunQueueState#QUEUED} row with no activity for too long - e.g. nothing
 *       currently running matches its version or taints - is marked {@link
 *       JobRunQueueState#CANCELLED} rather than left queued forever; a {@code CANCELLED} row is in
 *       turn purged once it's been sitting past its own retention
 * </ul>
 *
 * Liveness of a still-running job (its runner died or is stuck) is not tracked here - {@code
 * JobStateWatcher} covers that at the job-run level.
 */
@Service
@Profile("!job-run")
public class JobQueueWatcherService {

    private static final Logger logger = LoggerFactory.getLogger(JobQueueWatcherService.class);

    private final JobService jobService;
    private final JobDataService jobDataService;

    private final SingleCache<Map<Integer, Duration>> jobClaimTimeoutCache;
    private final SingleCache<Map<Integer, Duration>> jobQueueTimeoutCache;
    private final SingleCache<Map<Integer, Duration>> jobCancelledQueueRetentionCache;

    public JobQueueWatcherService(
            final JobService jobService, final JobDataService jobDataService) {
        this.jobService = jobService;
        this.jobDataService = jobDataService;
        this.jobClaimTimeoutCache =
                new SingleCache<>(
                        Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).build());
        this.jobQueueTimeoutCache =
                new SingleCache<>(
                        Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).build());
        this.jobCancelledQueueRetentionCache =
                new SingleCache<>(
                        Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).build());
    }

    /**
     * Per-job {@link ch.uetlib.job.model.properties.JobPropertyDefinitions#CLAIM_TIMEOUT}
     * overrides, cached for 30s - not computed once at startup, since an operator can change this
     * property live via the job properties API and this watcher should pick that up without
     * requiring a restart.
     */
    private Map<Integer, Duration> jobClaimTimeout() {
        return jobClaimTimeoutCache.get(() -> jobService.propertyValueByJobId(CLAIM_TIMEOUT));
    }

    /**
     * Per-job {@link ch.uetlib.job.model.properties.JobPropertyDefinitions#QUEUE_TIMEOUT}
     * overrides, cached the same way as {@link #jobClaimTimeout()}.
     */
    private Map<Integer, Duration> jobQueueTimeout() {
        return jobQueueTimeoutCache.get(() -> jobService.propertyValueByJobId(QUEUE_TIMEOUT));
    }

    /**
     * Per-job {@link
     * ch.uetlib.job.model.properties.JobPropertyDefinitions#CANCELLED_QUEUE_RETENTION}
     * overrides, cached the same way as {@link #jobClaimTimeout()}.
     */
    private Map<Integer, Duration> jobCancelledQueueRetention() {
        return jobCancelledQueueRetentionCache.get(
                () -> jobService.propertyValueByJobId(CANCELLED_QUEUE_RETENTION));
    }

    @Scheduled(cron = "${ch.uetlib.job.runner.reset-abandoned-claims.cron:14/15 * * * * ?}")
    public void resetAbandonedClaims() {
        final var staleBeforeByJobId =
                jobClaimTimeout().entrySet().stream()
                        .collect(MapCollectors.entriesToMapWithValueMapper(Instant.now()::minus));
        final var reset = jobDataService.resetStaleClaims(staleBeforeByJobId);
        if (reset > 0) {
            logger.info("Reset {} abandoned claim(s) back to queued.", reset);
        }
    }

    @Scheduled(cron = "${ch.uetlib.job.runner.reconcile-running.cron:13/15 * * * * ?}")
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
            jobDataService.resetQueuedRunById(queuedRun.jobQueuedRunId(), null);
            return;
        }

        final var jobRun = runsById.get(jobRunId);
        if (jobRun == null || !jobRun.latestState().isRunning()) {
            // The linked run has finished (or vanished); its RUNNING queue row is now stale.
            // Nothing deletes a RUNNING row on completion - not its own runner, not whatever
            // finished the run out of band (JobStateWatcher, KubernetesJobWatcher, a manual
            // cancel) - so this is where it gets cleaned up.
            jobDataService.deleteQueuedRunById(queuedRun.jobQueuedRunId());
        }
    }

    /**
     * Marks {@code QUEUED} rows unclaimable (no activity since {@code QUEUE_TIMEOUT}, e.g. a run
     * enqueued by a version or with taints nothing currently running matches - see {@link
     * ch.uetlib.job.service.executor.JobRunQueueService#claimIfMatchesThisRunner}) as
     * {@link JobRunQueueState#CANCELLED}. Left in the queue - not deleted - for visibility until
     * acknowledged or purged (see {@link #deleteOldCancelledQueuedRuns}).
     */
    @Scheduled(cron = "${ch.uetlib.job.runner.expire-unclaimable-queued.cron:37 * * * * ?}")
    public void expireUnclaimableQueuedRuns() {
        final var staleBeforeByJobId =
                jobQueueTimeout().entrySet().stream()
                        .collect(MapCollectors.entriesToMapWithValueMapper(Instant.now()::minus));
        final var expired = jobDataService.expireStaleQueuedRuns(staleBeforeByJobId);
        if (expired > 0) {
            logger.info("Marked {} unclaimable queued run(s) as cancelled.", expired);
        }
    }

    /**
     * Purges {@link JobRunQueueState#CANCELLED} rows past {@code CANCELLED_QUEUE_RETENTION} -
     * cleanup only, not a liveness concern, hence the much coarser cadence than the other watchers
     * here.
     */
    @Scheduled(
            cron = "${ch.uetlib.job.runner.delete-old-cancelled-queued.cron:0 41 * * * ?}")
    public void deleteOldCancelledQueuedRuns() {
        final var retainAfterByJobId =
                jobCancelledQueueRetention().entrySet().stream()
                        .collect(MapCollectors.entriesToMapWithValueMapper(Instant.now()::minus));
        final var deleted = jobDataService.deleteOldCancelledQueuedRuns(retainAfterByJobId);
        if (deleted > 0) {
            logger.info("Deleted {} cancelled queued run(s) past their retention.", deleted);
        }
        // The sweep above cannot reach a row whose job this build doesn't know; those get the
        // property's default retention, there being no job to read an override from.
        final var orphansDeleted =
                jobDataService.deleteOldCancelledQueuedRunsForUnknownJobs(
                        retainAfterByJobId.keySet(),
                        Instant.now()
                                .minus(CANCELLED_QUEUE_RETENTION.defaultValue().orElseThrow()));
        if (orphansDeleted > 0) {
            logger.info(
                    "Deleted {} cancelled queued run(s) of unknown jobs past the default retention.",
                    orphansDeleted);
        }
    }
}
