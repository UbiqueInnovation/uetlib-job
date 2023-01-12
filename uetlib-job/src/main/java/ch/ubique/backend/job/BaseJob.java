package ch.ubique.backend.job;

import static java.nio.charset.StandardCharsets.UTF_8;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.highmem.HighMemoryJobService;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.quartz.CronExpression;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;

public abstract class BaseJob implements org.quartz.Job, Runnable {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @NotNull protected final Job job;
    @NotNull protected final JobRun jobRun;
    @NotNull protected final ApplicationContext context;
    @NotNull protected final JobDataService jobDataService;
    @Nullable private final HighMemoryJobService highMemoryJobService;

    private Map<String, String> previousMDCContext;
    private Path logOutput;
    protected JobDataMap jobDataMap;

    protected BaseJob(@NotNull final ApplicationContext context) {
        this.context = context;
        this.jobDataService = context.getBean(JobDataService.class);
        this.highMemoryJobService =
                context.getBeansOfType(HighMemoryJobService.class).values().stream()
                        .findAny()
                        .orElse(null);

        this.job = jobDataService.registerJob(Job.fromClass(getClass()));
        this.jobRun = jobDataService.newRunInstance();
        this.jobDataMap = new JobDataMap();
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

    @NotNull
    public final String getCronExpression() {
        final var fixedCron = fixedCronExpression();
        if (StringUtils.isNotBlank(fixedCron)) {
            return fixedCron;
        }

        final var databaseCron = job.databaseCronExpression();
        if (StringUtils.isNotBlank(databaseCron)) {
            return databaseCron;
        }

        final var initialCron = initialCronExpression();
        if (StringUtils.isNotBlank(initialCron)) {
            return initialCron;
        }

        return "";
    }

    public final boolean isCronFixed() {
        return fixedCronExpression() != null;
    }

    public final boolean isCronExpressionValid() {
        return StringUtils.isBlank(getCronExpression())
                || CronExpression.isValidExpression(getCronExpression());
    }

    public final boolean isCronActive() {
        return StringUtils.isNotBlank(getCronExpression()) && isCronExpressionValid();
    }

    @Override
    public final void run() {
        run(true);
    }

    public final void run(boolean mayUseHighMemoryJobService) {
        if (isHighMemoryJob() && mayUseHighMemoryJobService) {
            logger.info("Run called for high memory job {}.", this);
            if (highMemoryJobService == null) {
                throw new UnsupportedOperationException(
                        "You need to configure a high memory job service.");
            }
            highMemoryJobService.run(this);
            return;
        }

        setupLog();
        logger.info("Running Job {}", getClass().getSimpleName());
        updateJobRunStart();
        try {
            final var jobState = internalRun();
            logger.info("Job finished in state {}", jobState);
            updateJobRunEnd(jobState);
        } catch (InterruptedException e) {
            logger.warn("Thread interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Exception running job: ", e);
            updateJobRunEnd(JobRunState.ERROR);
        }
        tearDownLog();
        System.gc();
    }

    @Override
    public final void execute(JobExecutionContext jobExecutionContext) {
        jobDataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        run();
    }

    @NotNull
    protected abstract JobRunState internalRun() throws Exception;

    private void updateJobRunStart() {
        jobRun.setState(JobRunState.BUSY);
        jobRun.setStartedAt(Instant.now());
        jobRun.setJobId(job.id());
        jobRun.setJobVersion(version());
        Optional.ofNullable(logOutput)
                .map(lo -> "file:" + lo.toFile().getAbsolutePath())
                .ifPresent(jobRun::setLogOutput);
        jobRun.setId(jobDataService.saveJobRun(jobRun).getId());
    }

    private void updateJobRunEnd(JobRunState finalState) {
        jobRun.setState(finalState);
        jobRun.setFinishedAt(Instant.now());
        jobRun.setLogOutput(getLogOutputContent());
        jobDataService.saveJobRun(jobRun);
    }

    private void setupLog() {
        previousMDCContext = null;
        try {
            previousMDCContext = MDC.getCopyOfContextMap();
            logOutput = Files.createTempFile(getClass().getName(), ".log");

            MDC.put("logFileName", logOutput.toAbsolutePath().toString());
        } catch (Exception e) {
            logger.error("Exception setting up thread aware logging: ", e);
        }
    }

    private void tearDownLog() {
        if (previousMDCContext != null) {
            MDC.setContextMap(previousMDCContext);
        }
        try {
            final var logContent = FileUtils.readFileToString(logOutput.toFile(), UTF_8);
            jobRun.setLogOutput(logContent);
            jobDataService.saveJobRun(jobRun);
        } catch (IOException e) {
            logger.error("Exception reading log from file: ", e);
        }
        MDC.remove("logFileName");
        FileUtils.deleteQuietly(logOutput.toFile());
    }

    @Nullable
    private String getLogOutputContent() {
        String logOutputText = null;
        if (logOutput != null) {
            try {
                logOutputText = FileUtils.readFileToString(logOutput.toFile(), UTF_8);
            } catch (final Exception e) {
                logger.warn("Could not read content from job run log file.", e);
            }
        }
        return logOutputText;
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

    /**
     * This method can be overwritten to indicate that the given job needs a lot of memory. If this
     * method returns true, the job is executed as a separate kubernetes job.
     */
    public boolean isHighMemoryJob() {
        return false;
    }
}
