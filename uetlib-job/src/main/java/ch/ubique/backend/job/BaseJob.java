package ch.ubique.backend.job;

import static java.nio.charset.StandardCharsets.UTF_8;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobState;
import ch.ubique.backend.job.model.entity.JobEntity;
import ch.ubique.backend.job.model.entity.JobRunEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public abstract class BaseJob implements Job, Runnable {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final JobEntity job;
    protected final JobRunEntity jobRun;
    protected final JobDataService jobDataService;

    private Map<String, String> previousMDCContext;
    private Path logOutput;

    protected BaseJob(final JobDataService jobDataService) {
        this.jobDataService = jobDataService;
        this.job = jobDataService.registerJobByClass(getClass());
        this.jobRun = new JobRunEntity();
    }

    @Override
    public void run() {
        setupLog();
        logger.info("Running Job {}", getClass().getSimpleName());
        updateJobRunStart();
        try {
            final var jobState = internalRun();
            logger.info("Job finished in state {}", jobState);
            updateJobRunEnd(jobState);
        } catch (Exception e) {
            logger.error("Exception running job: ", e);
            updateJobRunEnd(JobState.ERROR);
        }
        tearDownLog();
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        run();
    }

    protected abstract JobState internalRun();

    private void updateJobRunStart() {
        jobRun.setState(JobState.BUSY);
        jobRun.setStartedAt(Instant.now());
        jobRun.setJobId(job.getId());
        if (logOutput != null) {
            jobRun.setLogOutput("file:" + this.logOutput.toFile().getAbsolutePath());
        }
        jobRun.setId(jobDataService.saveJobRun(jobRun).getId());
    }

    private void updateJobRunEnd(JobState finalState) {
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

    private String getLogOutputContent() {
        String logOutputText = null;
        if (logOutput != null) {
            try {
                logOutputText = FileUtils.readFileToString(logOutput.toFile(), UTF_8);
            } catch (Exception e) {
                logger.warn("Could not read content from job run log file.", e);
            }
        }
        return logOutputText;
    }
}
