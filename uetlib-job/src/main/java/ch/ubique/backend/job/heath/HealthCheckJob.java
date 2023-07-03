package ch.ubique.backend.job.heath;

import ch.ubique.backend.job.BaseJob;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.ApplicationContext;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;

/** Checks if the job module is working properly. I.e. this is a self-check-job. */
public final class HealthCheckJob extends BaseJob {

    private final SchedulerCheckService schedulerCheckService;

    public HealthCheckJob(@NotNull final ApplicationContext context) {
        super(context);
        this.schedulerCheckService = context.getBean(SchedulerCheckService.class);
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
        return schedulerCheckService.checkSchedulerTriggers().orElse(JobRunState.DONE);
    }

    @Override
    public String fixedCronExpression() {
        return "0 */10 * ? * *";
    }

    @Override
    public @NotNull Collection<JobProperty<?>> fixedProperties() {
        return Set.of(
                JobPropertyDefinitions.LAST_RUN_MAX_AGE.createProperty(Duration.ofMinutes(15)),
                JobPropertyDefinitions.RUN_TIME_LIMIT.createProperty(Duration.ofMinutes(1)),
                JobPropertyDefinitions.CLEANUP_DAYS_TO_KEEP.createProperty(0),
                JobPropertyDefinitions.CLEANUP_CHUNK_SIZE.createProperty(10_000));
    }
}
