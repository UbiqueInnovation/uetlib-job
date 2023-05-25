package ch.ubique.backend.job.heath;

import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.scheduler.SchedulerService;

import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;

/** Checks if the job module is working properly. I.e. this is a self-check-job. */
@Service
public final class SchedulerCheckService {

    private final SchedulerService schedulerService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public SchedulerCheckService(final SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public Optional<JobRunState> checkSchedulerTriggers() {
        final Collection<TriggerKey> triggersInErrorState;
        try {
            triggersInErrorState = schedulerService.getTriggerKeys(Trigger.TriggerState.ERROR);
        } catch (final SchedulerException e) {
            logger.error("Could not get triggers in error state.", e);
            return Optional.of(JobRunState.ERROR);
        }

        if (triggersInErrorState.isEmpty()) {
            logger.info("No errors detected for scheduler triggers.");
            return Optional.empty();
        }

        logger.warn("Some triggers are in error state {}", triggersInErrorState);
        for (final var triggerKey : triggersInErrorState) {
            try {
                schedulerService.resetTriggerFromErrorState(triggerKey);
                logger.info("Reset {} from error state", triggerKey);
            } catch (final SchedulerException e) {
                logger.error("Could not reset {} from error state", triggerKey, e);
                return Optional.of(JobRunState.ERROR);
            }
        }
        return Optional.of(JobRunState.WARNING);
    }
}
