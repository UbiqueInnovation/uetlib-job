package ch.uetlib.job.service.watcher;

import ch.uetlib.core.utils.MapCollectors;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.properties.JobProperty;
import ch.uetlib.job.model.properties.JobPropertyDefinitions;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.executor.runners.JobRunner;
import ch.uetlib.job.service.logs.LogService;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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

    @Scheduled(cron = "${ch.uetlib.job.job-state-watcher.check-jobs-running.cron:0 */5 * * * ?}")
    public void checkIfJobsAreRunning() {
        logger.debug("Checking if jobs are running");
        final var running = jobService.findJobRunsByStates(JobRunState.RUNNING_STATES);
        for (final var jobRun : running) {
            try {
                checkIfJobIsRunning(jobRun);
            } catch (final Exception e) {
                // One run must not abort the sweep and leave every run behind it unchecked.
                logger.error("Could not check whether {} is running; skipping it.", jobRun, e);
            }
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

        final var state = findJobRunState(jobRun);
        if (state == null) {
            handleUnknownJobState(jobRun);
        } else {
            handleKnownJobState(jobRun, state);
        }
    }

    private void handleUnknownJobState(final JobRun jobRun) {
        if (wasRecentlyActive(jobRun)) {
            logger.debug("Could not resolve state for run {}, but was recently active.", jobRun);
            return;
        }

        // Set to unfinished
        logger.info(
                "Could not resolve state for run {}, and no recent activity. Setting job to {}",
                jobRun,
                JobRunState.UNFINISHED);
        setJobState(jobRun, JobRunState.UNFINISHED);
    }

    private void handleKnownJobState(final JobRun jobRun, final JobRunState state) {
        logger.debug("Resolved state {} for run {}.", state, jobRun);
        if (state.isRunning()) {
            logger.debug("{} is still active and in state {}.", jobRun, state);
        } else {
            setJobState(jobRun, state);
        }
    }

    private void setJobState(final JobRun jobRun, final JobRunState state) {
        final var previousContextMap = MDC.getCopyOfContextMap();
        MDC.put("jobRunId", String.valueOf(jobRun.id()));
        try {
            logger.debug("Inferred {} is now in state {}.", jobRun, state);
            jobService.updateJobRunState(jobRun, state, Instant.now());
            logger.info("Set job run {} to {}.", jobRun.id(), state);
        } finally {
            MDC.setContextMap(previousContextMap);
        }
    }

    private boolean wasRecentlyActive(final JobRun jobRun) {
        final var latestActivity = logService.mostRecentLogTimestamp(jobRun.id());
        final var timeout = latestActivityTimeout(jobRun);
        return latestActivity.isPresent()
                && latestActivity.get().isAfter(Instant.now().minus(timeout));
    }

    private @Nullable JobRunState findJobRunState(final JobRun jobRun) {
        final var runnerKey = jobRun.runnerKey();
        final var runner = jobRunners.get(runnerKey);
        if (runner == null) {
            logger.info("No runner found for {}.", runnerKey);
            return null;
        }
        final var resolvedState = runner.resolveJobRunState(jobRun);
        if (resolvedState == null) {
            logger.info(
                    "{} could not resolve job state for {}.",
                    runner.getClass().getSimpleName(),
                    jobRun);
            return null;
        }
        return resolvedState;
    }

    private Duration latestActivityTimeout(final JobRun jobRun) {
        final var runTimeLimit = getRunTimeLimit(jobRun);
        return runTimeLimit.orElse(Duration.ZERO).dividedBy(10).plus(Duration.ofMinutes(1));
    }

    private Optional<Duration> getRunTimeLimit(final JobRun jobRun) {
        return jobService
                .findJobById(jobRun.jobId())
                .flatMap(jobService::getJobClassMaybe)
                .map(jobService::instantiateJob)
                .map(jobService::getProperties)
                .map(p -> p.get(JobPropertyDefinitions.RUN_TIME_LIMIT))
                .flatMap(JobProperty::valueMaybe);
    }
}
