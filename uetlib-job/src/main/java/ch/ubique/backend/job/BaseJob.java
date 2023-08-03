package ch.ubique.backend.job;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinition;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.runners.JobRunner;
import ch.ubique.backend.job.runners.KubernetesJobRunner;
import ch.ubique.backend.job.service.JobService;

import io.kubernetes.client.openapi.ApiException;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.quartz.CronExpression;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class BaseJob implements org.quartz.Job, Runnable {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @NotNull protected final Job job;
    @NotNull protected final JobRun jobRun;
    @NotNull protected final ApplicationContext context;
    @NotNull protected final JobService jobService;
    @NotNull protected final JobDataService jobDataService;
    @NotNull private final Map<JobRunnerKey, JobRunner> jobRunners;

    protected JobDataMap jobDataMap;

    protected BaseJob(@NotNull final ApplicationContext context) {
        this.context = context;
        this.jobService = context.getBean(JobService.class);
        this.jobDataService = context.getBean(JobDataService.class);

        this.jobRunners = new EnumMap<>(JobRunnerKey.class);
        context.getBeansOfType(KubernetesJobRunner.class).values().stream()
                .findAny()
                .ifPresent(runner -> jobRunners.put(JobRunnerKey.DEDICATED_K8S_JOB, runner));

        this.job = jobDataService.registerJob(Job.fromClass(getClass()));
        this.jobRun = jobDataService.newRunInstance(job);
        this.jobDataMap = new JobDataMap();

        initializeProperties();
    }

    private void initializeProperties() {
        final var dbProperties = jobDataService.getProperties(job.id());
        final var fixedProperties = Objects.requireNonNull(fixedProperties());

        if (!fixedProperties().isEmpty() && !fixedProperties.equals(dbProperties)) {
            logger.warn(
                    "Properties in database are different to the fixed properties. Going to"
                            + " overwrite values in database.");
            jobDataService.clearProperties(job.id());
            jobDataService.saveProperties(job.id(), fixedProperties());
        }

        if (fixedProperties.isEmpty()) {
            final var dbPropertiesKeys =
                    dbProperties.stream()
                            .map(JobProperty::key)
                            .collect(Collectors.toUnmodifiableSet());

            Objects.requireNonNull(initialProperties())
                    .forEach(
                            property -> {
                                if (!dbPropertiesKeys.contains(property.definition().key())) {
                                    jobDataService.saveProperty(job.id(), property);
                                }
                            });
        }
    }

    public static boolean isCronExpressionValid(final String cronExpression) {
        return StringUtils.isBlank(cronExpression)
                || CronExpression.isValidExpression(cronExpression);
    }

    /**
     * This cron expression will be set if no cron expression is present in the database. Returns
     * null by default. If this method returns blank, no initial cron expression will be set.
     */
    @Nullable
    public String initialCronExpression() {
        return null;
    }

    /**
     * This cron expression will always be used and overrides the cron expression specified in the
     * database. Returns null by default. If this method returns blank, the cron expression in the
     * database will not be overwritten.
     */
    @Nullable
    public String fixedCronExpression() {
        return null;
    }

    /**
     * For each entry in this set: the property will be set if it is not returned by {@link
     * BaseJob#fixedProperties()}) and if it is not in the database yet.
     */
    @NotNull
    @SuppressWarnings("java:S1452")
    public Collection<JobProperty<?>> initialProperties() {
        return Set.of();
    }

    /**
     * If this set is non-empty, these properties will always be used. This will override properties
     * specified in the database.
     */
    @NotNull
    @SuppressWarnings("java:S1452")
    public Collection<JobProperty<?>> fixedProperties() {
        return Set.of();
    }

    @NotNull
    public final String getCronExpression() {
        if (StringUtils.isNotBlank(fixedCronExpression())) {
            final var fixedCron = fixedCronExpression();
            if (!isCronExpressionValid(fixedCron)) {
                throw new IllegalArgumentException(
                        MessageFormat.format(
                                "Could not instantiate job ''{0}'': fixedCronExpression is not"
                                        + " valid: ''{1}''",
                                job.name(), fixedCron));
            }
            return fixedCron;
        }

        if (StringUtils.isNotBlank(job.databaseCronExpression())) {
            return job.databaseCronExpression();
        }

        if (StringUtils.isNotBlank(initialCronExpression())) {
            final var initialCron = initialCronExpression();
            if (!isCronExpressionValid(initialCron)) {
                throw new IllegalArgumentException(
                        MessageFormat.format(
                                "Could not instantiate job ''{0}'': initialCronExpression is not"
                                        + " valid: ''{1}''",
                                job.name(), initialCron));
            }
            return initialCron;
        }

        return "";
    }

    public final boolean isCronFixed() {
        return fixedCronExpression() != null;
    }

    public final boolean isCronExpressionValid() {
        return isCronExpressionValid(getCronExpression());
    }

    public final boolean isCronActive() {
        return StringUtils.isNotBlank(getCronExpression()) && isCronExpressionValid();
    }

    @Override
    public final void run() {
        run(true);
    }

    public final void run(final boolean mayUseExternalRunner) {
        updateJobRunStart();
        setupLog();
        logger.info("Running Job {}", getClass().getSimpleName());

        final var jobState = runJob(mayUseExternalRunner);
        logger.info("Done. Job state: {}", jobState);

        updateJobRunScheduledOrEnd(jobState);

        System.gc();
        tearDownLog();
    }

    public final JobRunState runJob(final boolean mayUseExternalRunner) {
        final var runner =
                jobDataService.getProperty(job.id(), JobPropertyDefinitions.RUNNER).value();

        if (runner != JobRunnerKey.CURRENT && mayUseExternalRunner) {
            logger.info("Run called for dedicated job instance on external runner {}.", this);
            final var externalJobRunner = jobRunners.get(runner);
            if (externalJobRunner == null) {
                throw new NoSuchElementException("Could not find a runner for " + runner);
            }

            try {
                externalJobRunner.run(this);
                return JobRunState.SCHEDULED;
            } catch (final IOException e) {
                logger.error(
                        "Exception for dedicated job instance on external runner {}.", this, e);
                return JobRunState.ERROR;
            } catch (final ApiException e) {
                logger.error(
                        "Kubernetes Client API exception. responseBody = {}",
                        e.getResponseBody(),
                        e);
                return JobRunState.ERROR;
            }
        }

        try {
            return internalRun();
        } catch (final InterruptedException e) {
            logger.warn("Thread interrupted", e);
            Thread.currentThread().interrupt();
            return JobRunState.ERROR;
        } catch (final Exception e) {
            logger.error("Exception running job: ", e);
            return JobRunState.ERROR;
        }
    }

    @Override
    public final void execute(final JobExecutionContext jobExecutionContext) {
        jobDataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        run();
    }

    @NotNull
    protected abstract JobRunState internalRun() throws Exception;

    private void updateJobRunStart() {
        if (jobRun.getId() != null) {
            final var existingRun = jobDataService.findJobRunById(jobRun.getId()).orElseThrow();
            jobRun.setStartedAt(existingRun.getStartedAt());
        }

        jobRun.setState(JobRunState.BUSY);
        if (jobRun.getStartedAt() == null) {
            jobRun.setStartedAt(Instant.now());
        }
        jobRun.setJobId(job.id());
        jobRun.setJobVersion(version());
        jobRun.setId(jobDataService.saveJobRun(jobRun).getId());
    }

    private void updateJobRunScheduledOrEnd(final JobRunState state) {
        jobRun.setState(state);
        if (state != JobRunState.SCHEDULED) {
            jobRun.setFinishedAt(Instant.now());
        }
        jobDataService.saveJobRun(jobRun);
    }

    private void setupLog() {
        MDC.put("jobRunId", jobRun.getId().toString());
    }

    private void tearDownLog() {
        MDC.remove("jobRunId");
    }

    @NotNull
    public JobRunState state() {
        return jobRun.getState();
    }

    /**
     * The version is saved to the database for each job run. With this, we can track, what version
     * of the job was used for a given run.
     *
     * <p>The version is used by the {@link BaseInputJob} to determine, whether the job itself has
     * changed.
     */
    public int version() {
        return 0;
    }

    /**
     * Runs this job once when the application starts. This does not affect the regular
     * scheduling/trigger behaviour
     */
    public boolean runOnStartup() {
        return false;
    }

    public boolean isForceRunEnabled() {
        if (jobDataMap == null) {
            return false;
        }
        return jobDataMap.getBoolean("forceRun");
    }

    public JobRun jobRun() {
        return jobRun;
    }

    public <T> JobProperty<T> getProperty(final JobPropertyDefinition<T> definition) {
        return jobDataService.getProperty(job.id(), definition);
    }

    public Collection<JobProperty<Object>> getProperties() {
        return jobDataService.getProperties(job.id());
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("job", job)
                .append("jobRun", jobRun)
                .append("jobDataMap", jobDataMap)
                .toString();
    }

    public int priority() {
        return Trigger.DEFAULT_PRIORITY;
    }
}
