package ch.ubique.backend.job.heath;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobPropertiesConfig;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.triggers.CronTriggerDefinition;
import ch.ubique.backend.job.model.triggers.JobTriggersConfig;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.time.Duration;

/** Checks if the job module is working properly. I.e. this is a self-check-job. */
@RegisteredJob
public final class HealthCheckJob implements Job {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SchedulerCheckService schedulerCheckService;

    public HealthCheckJob(@NotNull final ApplicationContext context) {
        this.schedulerCheckService = context.getBean(SchedulerCheckService.class);
    }

    /**
     * @return We use the following semantic:
     *     <p>{@link JobRunState#ERROR} if an unfixable error was encountered
     *     <p>{@link JobRunState#WARNING} if an error was identified and a fix was available.
     *     <p>{@link JobRunState#DONE} if no issues were identified
     */
    @NotNull
    @Override
    public JobRunState run(final JobRunContext context) {
        logger.info("Performing health checks");
        return schedulerCheckService.checkSchedulerTriggers().orElse(JobRunState.DONE);
    }

    @Override
    public JobTriggersConfig triggersConfig() {
        return JobTriggersConfig.fixed(new CronTriggerDefinition("0 */10 * ? * *"));
    }

    @Override
    public JobPropertiesConfig propertiesConfig() {
        return JobPropertiesConfig.fixed(
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
