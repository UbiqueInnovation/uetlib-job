package ch.ubique.backend.job.service.executor.runners;

import static ch.ubique.backend.job.model.properties.JobPropertyDefinitions.RUNNER_GROUP;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.runner.RunnerGroup;
import ch.ubique.backend.job.service.JobService;

import jakarta.annotation.Nullable;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    public @NotNull JobRunnerKey key() {
        return JobRunnerKey.CURRENT;
    }

    @Override
    public synchronized boolean submit(
            final @NotNull Job job, final @NotNull JobRunContext jobRunContext) {
        final var runnerGroup = runnerGroup(jobRunContext);
        if (!isRunnerGroupAllowed(job, runnerGroup)) {
            return false;
        }

        final var executorService = getExecutorService(runnerGroup);
        final var jobRunId = jobRunContext.jobRun().id();
        final var future =
                executorService.submit(
                        () -> {
                            JobRunnerHelper.run(job, jobService, jobRunContext);
                            futuresByJobRun.remove(jobRunId);
                        });
        futuresByJobRun.put(jobRunId, future);
        return true;
    }

    @Override
    public void cancel(final @NotNull JobRun jobRun) {
        final var future = futuresByJobRun.get(jobRun.id());
        if (future != null) {
            future.cancel(true);
            futuresByJobRun.remove(jobRun.id());
        }
    }

    @Override
    @Nullable
    public JobRunState resolveJobRunState(final @NotNull JobRun jobRun) {
        final var future = futuresByJobRun.get(jobRun.id());
        if (future == null) {
            return null;
        }
        if (future.isDone()) {
            futuresByJobRun.remove(jobRun.id());
            return switch (future.state()) {
                case SUCCESS -> JobRunState.DONE;
                case CANCELLED -> JobRunState.UNFINISHED;
                case FAILED -> JobRunState.ERROR;
                case RUNNING -> throw new IllegalStateException("isDone implies !RUNNING");
            };
        }
        // Not done yet -> job is running
        return JobRunState.BUSY;
    }

    private ExecutorService getExecutorService(final RunnerGroup runnerGroup) {
        return executorServices.computeIfAbsent(runnerGroup, this::createExecutorService);
    }

    private ThreadPoolExecutor createExecutorService(final RunnerGroup runnerGroup) {
        final var nameFormat =
                runnerGroup.threadPoolSize() == 1
                        ? runnerGroup.identifier()
                        : runnerGroup.identifier() + "-%d";
        final var namedThreadFactory = new ThreadFactoryBuilder().setNameFormat(nameFormat).build();
        return new ThreadPoolExecutor(
                runnerGroup.threadPoolSize(),
                runnerGroup.threadPoolSize(),
                0,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                namedThreadFactory);
    }

    private boolean isRunnerGroupAllowed(final Job job, final RunnerGroup runnerGroup) {
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
}
