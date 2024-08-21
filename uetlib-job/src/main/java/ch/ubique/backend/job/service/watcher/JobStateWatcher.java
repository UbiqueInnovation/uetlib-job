package ch.ubique.backend.job.service.watcher;

import ch.ubique.backend.core.utils.MapCollectors;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.executor.runners.JobRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Profile("!job-run")
public class JobStateWatcher {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobDataService jobDataService;
    private final Map<JobRunnerKey, JobRunner> jobRunners;

    public JobStateWatcher(
            final JobDataService jobDataService, final Collection<JobRunner> jobRunners) {
        this.jobDataService = jobDataService;
        this.jobRunners = jobRunners.stream().collect(MapCollectors.toMap(JobRunner::key));
    }

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void checkIfJobsAreRunning() {
        logger.info("Checking if jobs are running");
        final var running = jobDataService.findJobRunByStates(JobRunState.RUNNING_STATES);
        for (final var jobRun : running) {
            final var state = isRunning(jobRun);
            if (state != JobRunState.BUSY) {
                logger.info("{} is not running.", jobRun);
                jobDataService.finishJobRun(jobRun.id(), state, Instant.now());
                logger.info("Set job run {} to {}.", jobRun.id(), state);
            }
        }
    }

    private JobRunState isRunning(final JobRun jobRun) {
        final var runnerKey = jobRun.runnerKey();
        final var runner = jobRunners.get(runnerKey);
        if (runner == null) {
            logger.info("No runner found for {}", runnerKey);
            return JobRunState.UNFINISHED;
        }
        return runner.isRunning(jobRun);
    }
}
