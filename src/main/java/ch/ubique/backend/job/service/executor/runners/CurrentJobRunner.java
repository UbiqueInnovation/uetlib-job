package ch.ubique.backend.job.service.executor.runners;

import static ch.ubique.backend.job.model.properties.JobPropertyDefinitions.RUNNER_GROUP;
import static java.util.Objects.requireNonNull;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.runner.RunnerGroup;
import ch.ubique.backend.job.service.JobService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.*;

@Service
@Profile("!job-run")
public final class CurrentJobRunner implements JobRunner {

    private static final Logger logger = LoggerFactory.getLogger(CurrentJobRunner.class);

    private final Map<RunnerGroup, ThreadPoolExecutor> executorServices;
    private final Map<Integer, Future<?>> futuresByJobRun;
    private final Map<Integer, RunnerGroup> runnerGroupByJobRun;
    private final Map<RunnerGroup, Collection<CurrentRunnerItem>> queuedJobRuns;
    private final Map<RunnerGroup, Collection<CurrentRunnerItem>> activeJobRuns;

    private final JobService jobService;

    public CurrentJobRunner(final JobService jobService) {
        this.jobService = jobService;
        executorServices = new ConcurrentHashMap<>();
        futuresByJobRun = new ConcurrentHashMap<>();
        runnerGroupByJobRun = new ConcurrentHashMap<>();
        queuedJobRuns = new ConcurrentHashMap<>();
        activeJobRuns = new ConcurrentHashMap<>();
    }

    public Map<RunnerGroup, ThreadPoolExecutor> getExecutorServices() {
        return Collections.unmodifiableMap(executorServices);
    }

    public Map<RunnerGroup, Collection<CurrentRunnerItem>> getQueuedJobRuns() {
        return Collections.unmodifiableMap(queuedJobRuns);
    }

    public Map<RunnerGroup, Collection<CurrentRunnerItem>> getActiveJobRuns() {
        return Collections.unmodifiableMap(activeJobRuns);
    }

    private static RunnerGroup runnerGroup(final JobRunContext jobRunContext) {
        return requireNonNull(
                jobRunContext.properties().get(RUNNER_GROUP).value());
    }

    @Override
    public JobRunnerKey key() {
        return JobRunnerKey.CURRENT;
    }

    @Override
    public synchronized boolean submit(
            final Job job, final JobRunContext jobRunContext) {
        final var runnerGroup = runnerGroup(jobRunContext);
        if (!isRunnerGroupAllowed(job, runnerGroup)) {
            return false;
        }

        final var runnerGroupQueue =
                queuedJobRuns.computeIfAbsent(
                        runnerGroup, ignored -> new ConcurrentLinkedDeque<>());
        final var runnerGroupActive =
                activeJobRuns.computeIfAbsent(
                        runnerGroup, ignored -> new ConcurrentLinkedDeque<>());
        final var jobRunId = jobRunContext.jobRun().id();
        runnerGroupByJobRun.put(jobRunId, runnerGroup);
        final var item = new CurrentRunnerItem(job.getClass(), jobRunContext.jobRun().id());
        runnerGroupQueue.add(item);
        final var executorService = getExecutorService(runnerGroup);
        final var future =
                executorService.submit(
                        () -> {
                            try {
                                runnerGroupActive.add(item);
                                runnerGroupQueue.remove(item);
                                JobRunnerHelper.run(job, jobService, jobRunContext);
                                futuresByJobRun.remove(jobRunId);
                                runnerGroupByJobRun.remove(jobRunId);
                            } finally {
                                runnerGroupActive.remove(item);
                            }
                        });
        futuresByJobRun.put(jobRunId, future);
        return true;
    }

    @Override
    public void cancel(final JobRun jobRun) {
        final var runId = jobRun.id();
        final var future = futuresByJobRun.get(runId);
        if (future != null) {
            future.cancel(true);
        }
        cleanupRun(runId);
    }

    private void cleanupRun(final int runId) {
        final var runnerGroup = runnerGroupByJobRun.get(runId);
        if (runnerGroup != null) {
            getExecutorService(runnerGroup).purge(); // Removes canceled tasks from the queue
            final var queued = queuedJobRuns.get(runnerGroup);
            if (queued != null) {
                queued.removeIf(e -> e.jobRunId() == runId);
            }
            final var active = activeJobRuns.get(runnerGroup);
            if (active != null) {
                active.removeIf(e -> e.jobRunId() == runId);
            }
        }
        runnerGroupByJobRun.remove(runId);
        futuresByJobRun.remove(runId);
    }

    @Override
    @Nullable
    public JobRunState resolveJobRunState(final  JobRun jobRun) {
        final var future = futuresByJobRun.get(jobRun.id());
        if (future == null) {
            return null;
        }
        if (!future.isDone()) {
            return JobRunState.BUSY;
        }
        if (future.isCancelled()) {
            return JobRunState.CANCELLED;
        }

        final var previousContextMap = MDC.getCopyOfContextMap();
        MDC.put("jobRunId", String.valueOf(jobRun.id()));
        try {
            future.get();
            return JobRunState.DONE;
        } catch (final InterruptedException e) {
            logger.info("Thread interrupted while resolving job run state.", e);
            Thread.currentThread().interrupt();
            return null;
        } catch (final ExecutionException e) {
            logger.info("Detected job run in error state.", e);
            cleanupRun(jobRun.id());
            return JobRunState.ERROR;
        } finally {
            MDC.setContextMap(previousContextMap);
        }
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
