package ch.uetlib.job.service.executor;

import static ch.uetlib.job.model.properties.JobPropertyDefinitions.CANCELLED_QUEUE_RETENTION;
import static ch.uetlib.job.model.properties.JobPropertyDefinitions.CLAIM_TIMEOUT;
import static ch.uetlib.job.model.properties.JobPropertyDefinitions.HEARTBEAT_TIMEOUT;
import static ch.uetlib.job.model.properties.JobPropertyDefinitions.QUEUE_TIMEOUT;

import static java.util.Objects.requireNonNull;

import ch.uetlib.core.utils.MapCollectors;
import ch.uetlib.core.utils.SingleCache;
import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.executor.heartbeat.HeartbeatRegistry;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keeps {@code t_job_run_queue} honest once a row has been claimed - or never gets claimed at all:
 *
 * <ul>
 *   <li>a row stuck in {@link JobRunQueueState#CLAIMED} (the claiming instance died before ever
 *       submitting, so it never reached {@link JobRunQueueState#RUNNING}) is reset back to {@link
 *       JobRunQueueState#QUEUED} for another instance to retry
 *   <li>a {@link JobRunQueueState#RUNNING} row whose linked job run has actually finished is
 *       removed; one whose heartbeat has gone stale - the executing runner (see {@code
 *       JobRunnerHelper}) is no longer proving itself alive - is treated as abandoned: the job run
 *       is marked {@link JobRunState#UNFINISHED} (cause unknown from here) and the row is removed
 *   <li>a {@link JobRunQueueState#QUEUED} row with no activity for too long - e.g. nothing
 *       currently running matches its version or taints - is marked {@link
 *       JobRunQueueState#CANCELLED} rather than left queued forever; a {@code CANCELLED} row is in
 *       turn purged once it's been sitting past its own retention (see {@link
 *       #expireUnclaimableQueuedRuns} / {@link #deleteOldCancelledQueuedRuns})
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
    private final HeartbeatRegistry heartbeatRegistry;

    private final SingleCache<Map<Integer, Duration>> jobClaimTimeoutCache;
    private final SingleCache<Map<Integer, Duration>> jobHeartbeatTimeoutCache;
    private final SingleCache<Map<Integer, Duration>> jobQueueTimeoutCache;
    private final SingleCache<Map<Integer, Duration>> jobCancelledQueueRetentionCache;

    public JobQueueWatcherService(
            final JobService jobService,
            final JobDataService jobDataService,
            final HeartbeatRegistry heartbeatRegistry) {
        this.jobService = jobService;
        this.jobDataService = jobDataService;
        this.heartbeatRegistry = heartbeatRegistry;
        this.jobClaimTimeoutCache =
                new SingleCache<>(
                        Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).build());
        this.jobHeartbeatTimeoutCache =
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
                                                                .valueNonNull())));
    }

    /**
     * Per-job {@link
     * ch.uetlib.job.model.properties.JobPropertyDefinitions#HEARTBEAT_TIMEOUT} overrides,
     * cached the same way as {@link #jobClaimTimeout()}.
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
                                                                .valueNonNull())));
    }

    /**
     * Per-job {@link ch.uetlib.job.model.properties.JobPropertyDefinitions#QUEUE_TIMEOUT}
     * overrides, cached the same way as {@link #jobClaimTimeout()}.
     */
    private Map<Integer, Duration> jobQueueTimeout() {
        return jobQueueTimeoutCache.get(
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
                                                                .get(QUEUE_TIMEOUT)
                                                                .valueNonNull())));
    }

    /**
     * Per-job {@link
     * ch.uetlib.job.model.properties.JobPropertyDefinitions#CANCELLED_QUEUE_RETENTION}
     * overrides, cached the same way as {@link #jobClaimTimeout()}.
     */
    private Map<Integer, Duration> jobCancelledQueueRetention() {
        return jobCancelledQueueRetentionCache.get(
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
                                                                .get(CANCELLED_QUEUE_RETENTION)
                                                                .valueNonNull())));
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
            jobDataService.resetQueuedRunById(queuedRun.jobQueuedRunId());
            return;
        }

        final var jobRun = jobDataService.findJobRunById(jobRunId);
        if (jobRun.isEmpty() || !jobRun.get().latestState().isRunning()) {
            // Its own runner should already have stopped this locally (see JobRunnerHelper), but
            // stop it here too in case something else caused the run to finish without that path
            // ever firing - e.g. its state was changed directly (KubernetesJobWatcher) while a
            // stuck-but-alive worker thread's own heartbeat never noticed on its own.
            heartbeatRegistry.stop(jobRunId);
            jobDataService.deleteQueuedRunById(queuedRun.jobQueuedRunId());
            return;
        }

        if (isHeartbeatStale(queuedRun)) {
            abandon(queuedRun, jobRun.get());
        }
    }

    private boolean isHeartbeatStale(final JobQueuedRun queuedRun) {
        final var timeout = requireNonNull(jobHeartbeatTimeout().get(queuedRun.jobId()));
        return queuedRun.isHeartbeatStale(timeout, Instant.now());
    }

    /**
     * {@link #reconcileRunningQueuedRuns} runs on every {@code !job-run} instance without regard to
     * which one is recorded as {@link JobQueuedRun#runner()} - it may well be this same instance
     * reconciling its own stuck job. Either way, this only has a stale heartbeat to go on, not the
     * actual cause (dead runner, stuck runner, network partition, ...), so it uses {@link
     * JobRunState#UNFINISHED} rather than {@link JobRunState#ERROR} or {@link
     * JobRunState#CANCELLED}, both of which would imply a known cause.
     */
    private void abandon(final JobQueuedRun queuedRun, final JobRun jobRun) {
        logger.warn(
                "Queued run {} has a stale heartbeat; its runner appears to be dead or stuck."
                        + " Marking job run {} as unfinished.",
                queuedRun,
                jobRun);
        jobService.updateJobRunState(jobRun, JobRunState.UNFINISHED, Instant.now());
        jobDataService.deleteQueuedRunById(queuedRun.jobQueuedRunId());
        // Closes the one gap a stale-heartbeat-only check can't: a worker thread that's alive but
        // truly stuck (e.g. a permanent deadlock) never dies, so its own ThreadProgressCheckIn
        // would
        // otherwise keep ticking here forever even after this instance itself gave up on it.
        heartbeatRegistry.stop(jobRun.id());
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
    }
}
