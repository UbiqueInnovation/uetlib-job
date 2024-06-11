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

    private JobRunnerHelper() {}

    private static final Logger logger = LoggerFactory.getLogger(JobRunnerHelper.class);

    static void run(
            final Job job, final JobService jobService, final JobRunContext initialContext) {
        final var jobRunContext = prepareRun(jobService, initialContext);
        final var state = runJob(job, jobRunContext);
        finishRun(jobService, jobRunContext, state);
    }

    static JobRunContext prepareRun(
            final JobService jobService, final JobRunContext initialContext) {
        final var jobRunContext =
                new JobRunContext(
                        initialContext.job(),
                        initialContext
                                .jobRun()
                                .modify()
                                .startedAt(Instant.now())
                                .state(JobRunState.BUSY)
                                .toJobRun(),
                        initialContext.arguments(),
                        initialContext.properties());
        jobService.updateJobRun(jobRunContext.jobRun());
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
        jobService.updateJobRun(
                jobRunContext
                        .jobRun()
                        .modify()
                        .state(finalState)
                        .finishedAt(finishedAt)
                        .toJobRun());
        MDC.remove("jobRunId");
    }
}
