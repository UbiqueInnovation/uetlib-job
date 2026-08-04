package ch.ubique.backend.job.service.executor;

import static ch.ubique.backend.job.model.properties.JobPropertyDefinitions.CLAIM_TIMEOUT;

import ch.ubique.backend.core.utils.MapCollectors;
import ch.ubique.backend.core.utils.SingleCache;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobQueuedRun;
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
 *   <li>a {@link JobRunQueueState#RUNNING} row is reconciled against its linked job run's actual
 *       state: removed once that run has finished, otherwise its heartbeat is refreshed
 * </ul>
 *
 * Deliberately does not duplicate stuck/timed-out-job-run detection - that's already handled at the
 * job run level (e.g. {@code JobTimeoutWatcher}). This only concerns the queue row's own
 * bookkeeping.
 */
@Service
@Profile("!job-run")
public class JobQueueWatcherService {

    private static final Logger logger = LoggerFactory.getLogger(JobQueueWatcherService.class);

    private final JobService jobService;
    private final JobDataService jobDataService;
    private final Duration defaultClaimTimeout;

    private final SingleCache<Map<Integer, Duration>> jobClaimTimeoutCache;

    public JobQueueWatcherService(
            final JobService jobService,
            final JobDataService jobDataService,
            @Value("${ch.ubique.backend.job.runner.claim.timeout:2m}")
                    final Duration defaultClaimTimeout) {
        this.jobService = jobService;
        this.jobDataService = jobDataService;
        this.defaultClaimTimeout = defaultClaimTimeout;
        this.jobClaimTimeoutCache =
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

        jobDataService.touchQueuedRunHeartbeat(queuedRun.jobQueuedRunId());
    }
}
