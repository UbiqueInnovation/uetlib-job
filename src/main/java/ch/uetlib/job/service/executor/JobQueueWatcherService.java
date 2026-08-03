package ch.uetlib.job.service.executor;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.queue.JobRunQueueState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

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
 * Deliberately does not duplicate stuck/timed-out-job-run detection - that's already handled at
 * the job run level (e.g. {@code JobTimeoutWatcher}). This only concerns the queue row's own
 * bookkeeping.
 */
@Service
@Profile("!job-run")
public class JobQueueWatcherService {

    private static final Logger logger = LoggerFactory.getLogger(JobQueueWatcherService.class);

    private final JobDataService jobDataService;
    private final Duration claimTimeout;

    public JobQueueWatcherService(
            final JobDataService jobDataService,
            @Value("${ch.uetlib.job.runner.claim.timeout:2m}") final Duration claimTimeout) {
        this.jobDataService = jobDataService;
        this.claimTimeout = claimTimeout;
    }

    @Scheduled(
            cron =
                    "${ch.uetlib.job.runner.reset-abandoned-claims.cron:*/15 * * * * ?}")
    public void resetAbandonedClaims() {
        final var staleBefore = Instant.now().minus(claimTimeout);
        final var reset = jobDataService.resetStaleClaims(staleBefore);
        if (reset > 0) {
            logger.info("Reset {} abandoned claim(s) back to queued.", reset);
        }
    }

    @Scheduled(cron = "${ch.uetlib.job.runner.reconcile-running.cron:*/15 * * * * ?}")
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
