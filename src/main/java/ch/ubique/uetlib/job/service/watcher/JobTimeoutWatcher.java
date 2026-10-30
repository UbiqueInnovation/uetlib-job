package ch.ubique.uetlib.job.service.watcher;

import static ch.ubique.uetlib.job.logging.JobRunMdcContext.runWithJobRunMdcContext;
import static ch.ubique.uetlib.job.model.properties.JobPropertyDefinitions.*;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.compare.ComparableUtils.is;

import ch.ubique.uetlib.job.model.JobRun;
import ch.ubique.uetlib.job.model.JobRunState;
import ch.ubique.uetlib.job.model.properties.JobProperties;
import ch.ubique.uetlib.job.service.JobService;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Profile("!job-run")
public class JobTimeoutWatcher {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobService jobService;

    public JobTimeoutWatcher(final JobService jobService) {
        this.jobService = jobService;
    }

    @Scheduled(cron = "${ch.ubique.backend.job.job-timeout-watcher.cron:30 */5 * * * ?}")
    public void checkTimeout() {
        logger.debug("Checking if jobs exceeded their timeout.");
        final var running = jobService.findJobRunsByStates(JobRunState.RUNNING_STATES);
        for (final var jobRun : running) {
            checkTimeoutForJob(jobRun);
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
