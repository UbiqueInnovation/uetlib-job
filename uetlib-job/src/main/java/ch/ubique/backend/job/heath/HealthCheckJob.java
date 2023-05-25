package ch.ubique.backend.job.heath;

import ch.ubique.backend.job.BaseJob;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.scheduler.SchedulerService;

import jakarta.validation.constraints.NotNull;

import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.context.ApplicationContext;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/** Checks if the job module is working properly. I.e. this is a self-check-job. */
public final class HealthCheckJob extends BaseJob {

    private final SchedulerService schedulerService;

    public HealthCheckJob(@NotNull final ApplicationContext context) {
        super(context);
        this.schedulerService = context.getBean(SchedulerService.class);
    }

    /**
     * @return We use the following semantic:
     *     <p>{@link JobRunState#ERROR} if an unfixable error was encountered
     *     <p>{@link JobRunState#WARNING} if an error was identified and a fix was available.
     *     <p>{@link JobRunState#DONE} if no issues were identified
     */
    @Override
    protected @NotNull JobRunState internalRun() {
        logger.info("Performing health checks");
        return checkSchedulerTriggers().orElse(JobRunState.DONE);
    }

    @Override
    public String fixedCronExpression() {
        return "* * * ? * * *";
    }

    @Override
    public @NotNull Collection<JobProperty<?>> fixedProperties() {
        return Set.of(
                JobPropertyDefinitions.LAST_RUN_MAX_AGE.createProperty(Duration.ofMinutes(3)),
                JobPropertyDefinitions.RUN_TIME_LIMIT.createProperty(Duration.ofMinutes(1)),
                JobPropertyDefinitions.CLEANUP_DAYS_TO_KEEP.createProperty(1));
    }

    private Optional<JobRunState> checkSchedulerTriggers() {
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
