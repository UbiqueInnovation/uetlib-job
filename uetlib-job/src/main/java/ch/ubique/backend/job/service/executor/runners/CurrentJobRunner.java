package ch.ubique.backend.job.service.executor.runners;

import static ch.ubique.backend.job.model.properties.JobPropertyDefinitions.RUNNER_GROUP;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRun;
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

    private static final Logger logger = LoggerFactory.getLogger(CurrentJobRunner.class);

    private final Map<RunnerGroup, ExecutorService> executorServices;
    private final Map<Integer, Future<?>> futuresByJobRun;

    private final JobService jobService;

    public CurrentJobRunner(final JobService jobService) {
        this.jobService = jobService;
        executorServices = new ConcurrentHashMap<>();
        futuresByJobRun = new ConcurrentHashMap<>();
    }

    private static RunnerGroup runnerGroup(final JobRunContext jobRunContext) {
        return jobRunContext.properties().get(RUNNER_GROUP).value();
    }

    @Override
    public JobRunnerKey key() {
        return JobRunnerKey.CURRENT;
    }

    @Override
    public synchronized boolean submit(final Job job, final JobRunContext jobRunContext) {
        final var runnerGroup = runnerGroup(jobRunContext);
        if (!check(job, runnerGroup)) {
            return false;
        }

        final var executorService = getExecutorService(runnerGroup);
        final var future = executorService.submit(() -> run(job, jobRunContext));
        futuresByJobRun.put(jobRunContext.jobRun().id(), future);
        return true;
    }

    @Override
    public void cancel(final JobRun jobRun) {
        final var future = futuresByJobRun.get(jobRun.id());
        if (future != null) {
            future.cancel(true);
            futuresByJobRun.remove(jobRun.id());
        }
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
        final var jobRunContext = prepareRun(initialContext);
        final var state = runJob(job, jobRunContext);
        finishRun(jobRunContext, state);
    }

    private JobRunContext prepareRun(final JobRunContext initialContext) {
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

    private JobRunState runJob(final Job job, final JobRunContext jobRunContext) {
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

    private void finishRun(final JobRunContext jobRunContext, final JobRunState finalState) {
        jobService.updateJobRun(
                jobRunContext
                        .jobRun()
                        .modify()
                        .state(finalState)
                        .finishedAt(Instant.now())
                        .toJobRun());
        MDC.remove("jobRunId");
        futuresByJobRun.remove(jobRunContext.jobRun().id());
    }

    private boolean check(final Job job, final RunnerGroup runnerGroup) {
        if (runnerGroup.allowedJobs().isEmpty()
                || runnerGroup.allowedJobs().contains(job.getClass())) {
            return true;
        }

        logger.error(
                "The runner group only allows the following job classes: {} but a job of the"
                        + " following class was provided:  {}",
                runnerGroup.allowedJobs(),
                job.getClass());
        return false;
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
