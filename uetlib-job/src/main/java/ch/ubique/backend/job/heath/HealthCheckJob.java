package ch.ubique.backend.job.heath;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobPropertiesConfig;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.runner.RunnerGroup;
import ch.ubique.backend.job.model.triggers.FixedRateTrigger;
import ch.ubique.backend.job.model.triggers.JobTriggersConfig;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;

/** Checks if the job module is working properly. I.e. this is a self-check-job. */
@RegisteredJob
public final class HealthCheckJob implements Job {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * @return We use the following semantic:
     *     <p>{@link JobRunState#ERROR} if an unfixable error was encountered
     *     <p>{@link JobRunState#WARNING} if an error was identified and a fix was available.
     *     <p>{@link JobRunState#DONE} if no issues were identified
     */
    @NotNull
    @Override
    public JobRunState run(final JobRunContext context) {
        logger.info("Running health check.");
        return JobRunState.DONE;
    }

    @Override
    public JobTriggersConfig triggersConfig() {
        return JobTriggersConfig.fixed(new FixedRateTrigger(Duration.ofMinutes(10)));
    }

    @Override
    public JobPropertiesConfig propertiesConfig() {
        return JobPropertiesConfig.fixed(
                JobPropertyDefinitions.RUNNER_GROUP.createProperty(
                        new RunnerGroup("health-check", 1, Set.of(HealthCheckJob.class))),
                JobPropertyDefinitions.LAST_RUN_MAX_AGE.createProperty(Duration.ofMinutes(15)),
                JobPropertyDefinitions.RUN_TIME_LIMIT.createProperty(Duration.ofMinutes(1)),
                JobPropertyDefinitions.CLEANUP_DAYS_TO_KEEP.createProperty(0),
                JobPropertyDefinitions.CLEANUP_CHUNK_SIZE.createProperty(10_000));
    }

    @Override
    public int priority() {
        return 1_000;
    }
}
