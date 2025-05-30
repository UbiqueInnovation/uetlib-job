package ch.ubique.backend.job.service.watcher;

import ch.ubique.backend.core.utils.MapCollectors;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.executor.runners.JobRunner;
import ch.ubique.backend.job.service.logs.LogService;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@Service
@Profile("!job-run")
public class JobStateWatcher {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobService jobService;
    private final LogService logService;
    private final Map<JobRunnerKey, JobRunner> jobRunners;

    public JobStateWatcher(
            final JobService jobService,
            final LogService logService,
            final Collection<JobRunner> jobRunners) {
        this.jobService = jobService;
        this.logService = logService;
        this.jobRunners = jobRunners.stream().collect(MapCollectors.toMap(JobRunner::key));
    }

    @Scheduled(
            cron =
                    "${ch.ubique.backend.job.job-state-watcher.check-jobs-running.cron:0 */5 * * * ?}")
    public void checkIfJobsAreRunning() {
        logger.debug("Checking if jobs are running");
        final var running = jobService.findJobRunsByStates(JobRunState.RUNNING_STATES);
        for (final var jobRun : running) {
            checkIfJobIsRunning(jobRun);
        }
    }

    private void checkIfJobIsRunning(final JobRun jobRun) {

        // Check if the runner has changed since the last check
        final var currentRunnerKey = jobService.getJobRun(jobRun.id()).runnerKey();
        final var runnerChanged = currentRunnerKey != jobRun.runnerKey();
        if (runnerChanged) {
            logger.debug(
                    "Runner changed from {} to {}. Ignoring run.",
                    jobRun.runnerKey(),
                    currentRunnerKey);
            return;
        }

        // Check if the job is still running
        final var state = findJobRunState(jobRun);
        logger.debug("Resolved state {} for run {}.", state, jobRun);
        if (jobRun.runnerKey() == JobRunnerKey.DEDICATED_K8S_JOB
                && state == JobRunState.SCHEDULED) {
            try {
                final var jobClassAndId =
                        new JobClassAndId(
                                jobRun.jobId(),
                                jobService.getJobClass(jobRun.jobId()).getCanonicalName());
                final var maxScheduledDuration =
                        jobService
                                .getProperties(jobClassAndId)
                                .get(JobPropertyDefinitions.MAX_DURATION_IN_SCHEDULED_STATE);
                if (Duration.between(jobRun.startedAt(), Instant.now())
                        .minus(maxScheduledDuration.value())
                        .isNegative()) {
                    jobService.cancel(jobRun);
                    logger.info(
                            "Job {} cancelled, was SCHEDULED too long on {} runner.",
                            jobRun.jobId(),
                            jobRun.runnerKey());
                }
            } catch (final ClassNotFoundException e) {
                logger.info("Job class for {} not found.", jobRun.jobId());
            }
        }

        if (state.isRunning()) {
            logger.debug("{} is still active and in state {}.", jobRun, state);
            return;
        }

        // If the job still produced logs recently, we are not going to update its state
        final var latestActivity = logService.mostRecentLogTimestamp(jobRun.id());
        final var timeout = latestActivityTimeout(jobRun);
        if (latestActivity.isAfter(Instant.now().minus(timeout))) {
            logger.debug("Latest log activity of run {} is newer than {}.", jobRun, timeout);
            return;
        }

        logger.debug("{} is not running.", jobRun);
        jobService.updateJobRunState(jobRun, state, Instant.now());
        logger.info("Set job run {} to {}.", jobRun.id(), state);
    }

    @NotNull
    private JobRunState findJobRunState(final JobRun jobRun) {
        final var runnerKey = jobRun.runnerKey();
        final var runner = jobRunners.get(runnerKey);
        if (runner == null) {
            logger.info("No runner found for {}.", runnerKey);
            return JobRunState.UNFINISHED;
        }
        final var resolvedState = runner.resolveJobRunState(jobRun);
        if (resolvedState == null) {
            logger.info("Could not resolve job state.");
            return JobRunState.UNFINISHED;
        }
        return resolvedState;
    }

    private Duration latestActivityTimeout(final JobRun jobRun) {
        final var runTimeLimit = getRunTimeLimit(jobRun);
        return runTimeLimit.orElse(Duration.ZERO).dividedBy(10).plus(Duration.ofMinutes(1));
    }

    private Optional<Duration> getRunTimeLimit(final JobRun jobRun) {
        try {
            final var job = jobService.instantiateJob(jobRun.jobId());
            return jobService
                    .getProperties(job)
                    .get(JobPropertyDefinitions.RUN_TIME_LIMIT)
                    .valueMaybe();
        } catch (final ClassNotFoundException e) {
            logger.info("Could not get run time limit for {}.", jobRun, e);
            return Optional.empty();
        }
    }
}
