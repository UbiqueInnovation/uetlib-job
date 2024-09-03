package ch.ubique.backend.job.service.watcher;

import ch.ubique.backend.core.utils.MapCollectors;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.executor.runners.JobRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Profile("!job-run")
public class JobStateWatcher {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobService jobService;
    private final Map<JobRunnerKey, JobRunner> jobRunners;

    public JobStateWatcher(final JobService jobService, final Collection<JobRunner> jobRunners) {
        this.jobService = jobService;
        this.jobRunners = jobRunners.stream().collect(MapCollectors.toMap(JobRunner::key));
    }

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void checkIfJobsAreRunning() {
        logger.info("Checking if jobs are running");
        final var running = jobService.findJobRunsByStates(JobRunState.RUNNING_STATES);
        for (final var jobRun : running) {
            final var state = findJobRunState(jobRun);
            final var currentRunnerKey = jobService.getJobRun(jobRun.id()).runnerKey();
            final var runnerChanged = currentRunnerKey != jobRun.runnerKey();
            if (runnerChanged) {
                logger.info(
                        "Runner changed from {} to {}. Ignoring run.",
                        jobRun.runnerKey(),
                        currentRunnerKey);
            } else if (state.isEmpty()) {
                logger.info("Could not find job state, treating as UNFINISHED.");
                jobService.updateJobRunState(
                        jobRun,
                        JobRunState.UNFINISHED,
                        Instant.now(),
                        getClass().getCanonicalName() + "#checkIfJobsAreRunning -> state is empty");
            } else if (!state.get().isRunning()) {
                logger.info("{} is not running.", jobRun);
                jobService.updateJobRunState(
                        jobRun,
                        state.get(),
                        Instant.now(),
                        getClass().getCanonicalName()
                                + "#checkIfJobsAreRunning -> state is "
                                + state.get());
                logger.info("Set job run {} to {}.", jobRun.id(), state.get());
            }
        }
    }

    private Optional<JobRunState> findJobRunState(final JobRun jobRun) {
        final var runnerKey = jobRun.runnerKey();
        final var runner = jobRunners.get(runnerKey);
        if (runner == null) {
            logger.info("No runner found for {}", runnerKey);
            return Optional.of(JobRunState.UNFINISHED);
        }
        return Optional.ofNullable(runner.resolveJobRunState(jobRun));
    }
}
