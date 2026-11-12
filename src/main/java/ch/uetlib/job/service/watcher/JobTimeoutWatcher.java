package ch.uetlib.job.service.watcher;

import static ch.uetlib.job.logging.JobRunMdcContext.runWithJobRunMdcContext;
import static ch.uetlib.job.model.properties.JobPropertyDefinitions.*;

import static org.apache.commons.lang3.compare.ComparableUtils.is;

import static java.util.Objects.requireNonNull;

import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.service.JobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@Profile("!job-run")
public class JobTimeoutWatcher {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobService jobService;

    public JobTimeoutWatcher(final JobService jobService) {
        this.jobService = jobService;
    }

    @Scheduled(cron = "${ch.uetlib.job.job-timeout-watcher.cron:30 */5 * * * ?}")
    public void checkTimeout() {
        logger.debug("Checking if jobs exceeded their timeout.");
        final var running = jobService.findJobRunsByStates(JobRunState.RUNNING_STATES);
        for (final var jobRun : running) {
            try {
                checkTimeoutForJob(jobRun);
            } catch (final Exception e) {
                // One run must not abort the sweep and leave every run behind it unchecked.
                logger.error("Could not check {} for timeout; skipping it.", jobRun, e);
            }
        }
    }

    private void checkTimeoutForJob(final JobRun jobRun) {
        final var jobMaybe = jobService.findJobById(jobRun.jobId());
        if (jobMaybe.isEmpty()) {
            return;
        }
        final var jobClass = jobService.getJobClassMaybe(jobMaybe.get());
        if (jobClass.isEmpty()) {
            return;
        }

        final var job = jobService.instantiateJob(jobClass.get());
        final var sate = jobRun.latestState();
        final var properties = jobService.getProperties(job);

        if (!killTimeoutEnabled(properties, sate)) {
            logger.debug("Kill after timeout not enabled for {}.", job);
            return;
        }

        final var timeInState = Duration.between(jobRun.startedAt(), Instant.now());
        final var timeout = timeoutForState(properties, sate);
        final var gracePeriod = properties.getValue(KILL_GRACE_PERIOD);

        if (is(timeInState).greaterThan(timeout.plus(gracePeriod))) {
            runWithJobRunMdcContext(jobRun, () -> cancelRun(jobRun, sate));
        }
    }

    private void cancelRun(final JobRun jobRun, final JobRunState sate) {
        logger.info("{} exceeded its timeout plus grace period in state {}.", jobRun, sate);
        jobService.cancel(jobRun);
    }

    private Duration timeoutForState(final JobProperties properties, final JobRunState state) {
        final var value =
                switch (state) {
                    case SCHEDULED -> properties.getValue(MAX_DURATION_IN_SCHEDULED_STATE);
                    case BUSY -> properties.getValue(RUN_TIME_LIMIT);
                    default -> throw new IllegalArgumentException();
                };
        return requireNonNull(value);
    }

    private boolean killTimeoutEnabled(final JobProperties properties, final JobRunState state) {
        final var value =
                switch (state) {
                    case SCHEDULED -> properties.getValue(KILL_JOBS_SCHEDULE_TIMEOUT);
                    case BUSY -> properties.getValue(KILL_JOBS_RUN_TIMEOUT);
                    default -> throw new IllegalArgumentException();
                };
        return Boolean.TRUE.equals(value);
    }
}
