package ch.ubique.backend.job.service.executor.runners;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.service.JobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;

public class JobRunnerHelper {

    private static final Logger logger = LoggerFactory.getLogger(JobRunnerHelper.class);

    private JobRunnerHelper() {}

    static void run(
            final Job job, final JobService jobService, final JobRunContext initialContext) {
        final var jobRunContext = prepareRun(jobService, initialContext);
        final var state = runJob(job, jobRunContext);
        finishRun(jobService, jobRunContext, state);
    }

    static JobRunContext prepareRun(
            final JobService jobService, final JobRunContext initialContext) {
        final var startedJobRun = jobService.startJobRun(initialContext.jobRun(), Instant.now());
        final var jobRunContext =
                new JobRunContext(
                        initialContext.job(),
                        startedJobRun,
                        initialContext.arguments(),
                        initialContext.properties());
        MDC.put("jobRunId", String.valueOf(jobRunContext.jobRun().id()));
        return jobRunContext;
    }

    static JobRunState runJob(final Job job, final JobRunContext jobRunContext) {
        try {
            logger.info("Running job {} with jobRunContext {}", job, jobRunContext);
            final var state = job.run(jobRunContext);
            logger.info("Job {} finished with state {}", job, state);
            return state;
        } catch (final Exception e) {
            logger.error("Exception running job: ", e);
            return JobRunState.ERROR;
        }
    }

    static void finishRun(
            final JobService jobService,
            final JobRunContext jobRunContext,
            final JobRunState finalState) {
        final var finishedAt = finalState.isRunning() ? null : Instant.now();
        jobService.finishJobRun(jobRunContext.jobRun(), finalState, finishedAt);
        MDC.remove("jobRunId");
    }
}
