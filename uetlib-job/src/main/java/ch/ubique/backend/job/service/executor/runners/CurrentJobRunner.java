package ch.ubique.backend.job.service.executor.runners;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.JobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public final class CurrentJobRunner implements JobRunner {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ExecutorService executorService;

    private final JobService jobService;

    public CurrentJobRunner(final JobService jobService) {
        this.jobService = jobService;

        final var corePoolSize = 1;
        final var maximumPoolSize = 1;
        final var keepAliveTime = Duration.ZERO;
        executorService =
                new ThreadPoolExecutor(
                        corePoolSize,
                        maximumPoolSize,
                        keepAliveTime.toSeconds(),
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>());
    }

    @Override
    public JobRunnerKey key() {
        return JobRunnerKey.CURRENT;
    }

    @Override
    public synchronized JobRunState submit(final Job job, final JobRunContext context) {
        executorService.submit(() -> run(job, context));
        return JobRunState.SCHEDULED;
    }

    private void run(final Job job, final JobRunContext initialContext) {
        final var context = prepareRun(initialContext);
        final var state = runJob(job, context);
        finishRun(context, state);
    }

    private JobRunContext prepareRun(final JobRunContext initialContext) {
        final var context =
                new JobRunContext(
                        initialContext.job(),
                        initialContext.jobRun().modify().state(JobRunState.BUSY).toJobRun(),
                        initialContext.arguments(),
                        initialContext.properties());
        jobService.updateJobRun(context.jobRun());
        MDC.put("jobRunId", String.valueOf(context.jobRun().id()));
        return context;
    }

    private JobRunState runJob(final Job job, final JobRunContext context) {
        try {
            logger.info("Running job {} with context {}", job, context);
            final var state = job.run(context);
            logger.info("Job {} finished with state {}", job, state);
            return state;
        } catch (final Exception e) {
            logger.error("Exception running job: ", e);
            return JobRunState.ERROR;
        }
    }

    private void finishRun(final JobRunContext context, final JobRunState finalState) {
        jobService.updateJobRun(
                context.jobRun().modify().state(finalState).finishedAt(Instant.now()).toJobRun());
        MDC.remove("jobRunId");
    }
}
