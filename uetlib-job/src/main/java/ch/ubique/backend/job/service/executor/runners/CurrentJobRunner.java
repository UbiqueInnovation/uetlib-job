package ch.ubique.backend.job.service.executor.runners;

import static ch.ubique.backend.job.model.properties.JobPropertyDefinitions.RUNNER_GROUP;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.runner.RunnerGroup;
import ch.ubique.backend.job.service.JobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

@Service
public final class CurrentJobRunner implements JobRunner {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<RunnerGroup, ExecutorService> executorServices;

    private final JobService jobService;

    public CurrentJobRunner(final JobService jobService) {
        this.jobService = jobService;
        executorServices = new ConcurrentHashMap<>();
    }

    @Override
    public JobRunnerKey key() {
        return JobRunnerKey.CURRENT;
    }

    @Override
    public synchronized boolean submit(final Job job, final JobRunContext context) {
        final var runnerGroup = runnerGroup(context);
        if (!check(job, runnerGroup)) {
            return false;
        }

        final var executorService = getExecutorService(runnerGroup);
        executorService.submit(() -> run(job, context));
        return true;
    }

    private static RunnerGroup runnerGroup(final JobRunContext context) {
        return context.properties().get(RUNNER_GROUP).value();
    }

    private ExecutorService getExecutorService(final RunnerGroup runnerGroup) {
        return executorServices.computeIfAbsent(runnerGroup, this::createExecutorService);
    }

    private ThreadPoolExecutor createExecutorService(final RunnerGroup runnerGroup) {
        return new ThreadPoolExecutor(
                runnerGroup.threadPoolSize(),
                runnerGroup.threadPoolSize(),
                0,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());
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
                        initialContext
                                .jobRun()
                                .modify()
                                .startedAt(Instant.now())
                                .state(JobRunState.BUSY)
                                .toJobRun(),
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

    private boolean check(final Job job, final RunnerGroup runnerGroup) {
        if (!runnerGroup.allowedJobs().isEmpty()
                && !runnerGroup.allowedJobs().contains(job.getClass())) {
            logger.error(
                    "The runner group only allows the following job classes: {} but a job of the following class was provided:  {}",
                    runnerGroup.allowedJobs(),
                    job.getClass());
            return false;
        }
        return true;
    }

    public void shutdownAndWaitForJobsToComplete() {
        for (final var executorService : executorServices.values()) {
            executorService.shutdown();
            try {
                //noinspection ResultOfMethodCallIgnored
                executorService.awaitTermination(365, TimeUnit.DAYS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}
