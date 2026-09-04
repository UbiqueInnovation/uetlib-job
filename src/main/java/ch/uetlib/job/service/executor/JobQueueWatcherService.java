package ch.uetlib.job.service.executor;

import static ch.uetlib.job.model.properties.JobPropertyDefinitions.CANCELLED_QUEUE_RETENTION;
import static ch.uetlib.job.model.properties.JobPropertyDefinitions.CLAIM_TIMEOUT;
import static ch.uetlib.job.model.properties.JobPropertyDefinitions.QUEUE_TIMEOUT;

import ch.uetlib.core.utils.MapCollectors;
import ch.uetlib.core.utils.SingleCache;
import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.JobRun;
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
 * Reconciles {@code t_job_run_queue}: resets abandoned claims, removes rows of finished runs, and
 * cancels, then purges, rows nobody claims.
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
     * Per-job {@code CLAIM_TIMEOUT}, cached for 30s so live property changes apply without a
     * restart.
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

    /** Per-job {@code CANCELLED_QUEUE_RETENTION}, cached like {@link #jobClaimTimeout()}. */
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
        // One lookup for every linked run rather than one per row.
        final var runsById =
                jobDataService.findJobRunsByIds(
                        runningQueuedRuns.stream()
                                .map(JobQueuedRun::jobRunId)
                                .filter(Objects::nonNull)
                                .toList());
        runningQueuedRuns.forEach(queuedRun -> reconcile(queuedRun, runsById));
    }

    private void reconcile(final JobQueuedRun queuedRun, final Map<Integer, JobRun> runsById) {
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
     * Cancels {@code QUEUED} rows idle for {@code QUEUE_TIMEOUT}; they stay visible until
     * acknowledged or purged.
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
