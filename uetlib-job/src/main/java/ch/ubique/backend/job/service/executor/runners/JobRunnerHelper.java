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

        updateJobRunState(jobService, jobRunContext, state);
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
        if (state.isRunning()) {
            jobService.updateJobRunState(jobRunContext.jobRun(), state, transitionedAt);
        } else {
            jobService.finishJobRun(jobRunContext.jobRun(), state, transitionedAt);
        }
        MDC.remove("jobRunId");
    }
}
