package ch.ubique.backend.job.service.executor.runners;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.service.JobService;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Objects;

public class JobRunnerHelper {

    private static final Logger logger = LoggerFactory.getLogger(JobRunnerHelper.class);

    private JobRunnerHelper() {}

    static void run(
            final Job job, final JobService jobService, final JobRunContext initialContext) {
        final var jobRunContext = prepareRun(jobService, initialContext);
        final var previousContextMap = MDC.getCopyOfContextMap();
        MDC.put("jobRunId", String.valueOf(jobRunContext.jobRun().id()));
        runJob(job, jobRunContext, jobService);
        MDC.setContextMap(previousContextMap);
    }

    static JobRunContext prepareRun(
            final JobService jobService, final JobRunContext initialContext) {
        final var startedJobRun = jobService.startJobRun(initialContext.jobRun(), Instant.now());
        return new JobRunContext(
                initialContext.job(),
                startedJobRun,
                initialContext.arguments(),
                initialContext.properties());
    }

    static void runJob(
            final Job job, final JobRunContext jobRunContext, final JobService jobService) {
        try {
            logger.info("Running job {} with jobRunContext {}", job, jobRunContext);
            final var state = job.run(jobRunContext);
            logger.info("Job {} finished with state {}", job, state);
            updateJobRunState(jobService, jobRunContext, state);
        } catch (final Exception e) {
            final var rootCause = Objects.requireNonNullElse(ExceptionUtils.getRootCause(e), e);
            if (rootCause instanceof InterruptedException) {
                logger.info("Job {} interrupted", job);
                updateJobRunState(jobService, jobRunContext, JobRunState.UNFINISHED);
                Thread.currentThread().interrupt();
            } else {
                logger.error("Exception running job: ", e);
                updateJobRunState(jobService, jobRunContext, JobRunState.ERROR);
            }
        }
    }

    /**
     * In most cases, this is will finish the job and set the finishedAt timestamp. But, in the case
     * of a StateJob, this new state might {@link JobRunState#SCHEDULED} if the actual job is
     * scheduled as a new pod in the k8s.
     */
    static void updateJobRunState(
            final JobService jobService,
            final JobRunContext jobRunContext,
            final JobRunState state) {
        final var transitionedAt = Instant.now();
        jobService.updateJobRunState(jobRunContext.jobRun(), state, transitionedAt);
    }
}
