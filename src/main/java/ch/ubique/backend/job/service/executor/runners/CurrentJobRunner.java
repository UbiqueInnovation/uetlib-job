package ch.ubique.backend.job.service.executor.runners;

import static ch.ubique.backend.job.model.properties.JobPropertyDefinitions.RUNNER_GROUP;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.runner.RunnerGroup;
import ch.ubique.backend.job.service.JobService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.*;

@Service
public final class CurrentJobRunner implements JobRunner {

    private static final Logger logger = LoggerFactory.getLogger(CurrentJobRunner.class);

    private final Map<RunnerGroup, ThreadPoolExecutor> executorServices;
    private final Map<Integer, Future<?>> futuresByJobRun;
    private final Map<Integer, RunnerGroup> runnerGroupByJobRun;
    private final Map<RunnerGroup, Collection<CurrentRunnerQueueItem>> queuedJobRuns;

    private final JobService jobService;

    public CurrentJobRunner(final JobService jobService) {
        this.jobService = jobService;
        executorServices = new ConcurrentHashMap<>();
        futuresByJobRun = new ConcurrentHashMap<>();
        runnerGroupByJobRun = new ConcurrentHashMap<>();
        queuedJobRuns = new ConcurrentHashMap<>();
    }

    public Map<RunnerGroup, ThreadPoolExecutor> getExecutorServices() {
        return Collections.unmodifiableMap(executorServices);
    }

    public Map<RunnerGroup, Collection<CurrentRunnerQueueItem>> getQueuedJobRuns() {
        return Collections.unmodifiableMap(queuedJobRuns);
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
        runnerGroupByJobRun.put(jobRunId, runnerGroup);
        final var queueItem =
                new CurrentRunnerQueueItem(job.getClass(), jobRunContext.jobRun().id());
        final var runnerGroupQueue =
                queuedJobRuns.computeIfAbsent(
                        runnerGroup, ignored -> new ConcurrentLinkedDeque<>());
        runnerGroupQueue.add(queueItem);
        final var future =
                executorService.submit(
                        () -> {
                            runnerGroupQueue.remove(queueItem);
                            JobRunnerHelper.run(job, jobService, jobRunContext);
                            futuresByJobRun.remove(jobRunId);
                            runnerGroupByJobRun.remove(jobRunId);
                        });
        futuresByJobRun.put(jobRunId, future);
        return true;
    }

    @Override
    public void cancel(final @NotNull JobRun jobRun) {
        final var runId = jobRun.id();
        final var future = futuresByJobRun.get(runId);
        if (future != null) {
            future.cancel(true);
        }
        final var runnerGroup = runnerGroupByJobRun.get(runId);
        if (runnerGroup != null) {
            getExecutorService(runnerGroup).purge(); // Remove canceled tasks from the queue
            queuedJobRuns.get(runnerGroup).removeIf(e -> e.jobRunId() == runId);
        }
        runnerGroupByJobRun.remove(runId);
        futuresByJobRun.remove(runId);
    }

    @Override
    @Nullable
    public JobRunState resolveJobRunState(final @NotNull JobRun jobRun) {
        final var future = futuresByJobRun.get(jobRun.id());
        if (future == null) {
            return null;
        }
        if (future.isDone()) {
            return switch (future.state()) {
                case SUCCESS -> JobRunState.DONE;
                case CANCELLED -> JobRunState.CANCELLED;
                case FAILED -> JobRunState.ERROR;
                case RUNNING -> throw new IllegalStateException("isDone implies !RUNNING");
            };
        }
        // Not done yet -> the job is running
        return JobRunState.BUSY;
    }

    private ThreadPoolExecutor getExecutorService(final RunnerGroup runnerGroup) {
        return executorServices.computeIfAbsent(runnerGroup, this::createExecutorService);
    }

    private ThreadPoolExecutor createExecutorService(final RunnerGroup runnerGroup) {
        final var runnerGroupIdentifier = runnerGroup.identifier();
        final var runnerGroupThreadPoolSize = runnerGroup.threadPoolSize();

        final var nameFormat =
                runnerGroupThreadPoolSize == 1
                        ? runnerGroupIdentifier
                        : runnerGroupIdentifier + "-%d";
        final var namedThreadFactory = new ThreadFactoryBuilder().setNameFormat(nameFormat).build();

        return new ThreadPoolExecutor(
                runnerGroupThreadPoolSize,
                runnerGroupThreadPoolSize,
                0,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                namedThreadFactory);
    }

    private boolean isRunnerGroupAllowed(final Job job, final RunnerGroup runnerGroup) {
        final var runnerGroupAllowedJobs = runnerGroup.allowedJobs();
        if (runnerGroupAllowedJobs.isEmpty() || runnerGroupAllowedJobs.contains(job.getClass())) {
            return true;
        }

        logger.error(
                "The runner group only allows the following job classes: {} but a job of the"
                        + " following class was provided:  {}",
                runnerGroupAllowedJobs,
                job.getClass());
        return false;
    }
}
