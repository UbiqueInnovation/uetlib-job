package ch.ubique.backend.job.service.executor;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.*;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.executor.runners.JobRunner;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JobExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(JobExecutorService.class);

    private final JobService jobService;

    private final Map<JobRunnerKey, JobRunner> jobRunners;

    public JobExecutorService(final JobService jobService, final Collection<JobRunner> jobRunners) {
        this.jobService = jobService;
        this.jobRunners =
                jobRunners.stream().collect(Collectors.toMap(JobRunner::key, Function.identity()));
    }

    public void submit(@NotNull final Class<? extends Job> jobClass) {
        submit(jobClass, null);
    }

    public void submit(
            @NotNull final Class<? extends Job> jobClass,
            @Nullable final JobRunArguments arguments) {
        submit(jobClass, arguments, null, null);
    }

    public void submit(
            @NotNull final Class<? extends Job> jobClass,
            @Nullable final JobRunArguments arguments,
            @Nullable final Integer runId,
            @Nullable final JobRunnerKey jobRunnerKey) {

        final var job = jobService.instantiateJob(jobClass);
        if (!job.allowParallelRuns() && isCurrentlyRunning(job)) {
            logger.info("Job is already running and parallel runs are disallowed.");
            return;
        }

        logger.info("{} submitted to executor.", jobClass);
        final var jobRunContext = createContext(job, runId, arguments);
        final var runner = getJobRunner(jobRunContext, jobRunnerKey);

        MDC.put("jobRunId", String.valueOf(jobRunContext.jobRun().id()));

        final var scheduled = tryToSchedule(runner, job, jobRunContext);

        if (scheduled) {
            logger.info("Job {} successfully scheduled", job);
        } else {
            logger.error("Job {} could not be scheduled", job);
            jobService.updateJobRun(
                    jobRunContext
                            .jobRun()
                            .modify()
                            .state(JobRunState.ERROR)
                            .finishedAt(Instant.now())
                            .toJobRun());
        }

        MDC.remove("jobRunId");
    }

    private boolean tryToSchedule(
            final JobRunner runner, final Job job, final JobRunContext jobRunContext) {
        try {
            logger.info(
                    "Scheduling job {} with jobRunContext {} using runner {}",
                    job,
                    jobRunContext,
                    runner.getClass());
            return runner.submit(job, jobRunContext);
        } catch (final Exception e) {
            logger.warn("Unhandled exception while trying to schedule job.", e);
            return false;
        }
    }

    @NotNull
    private boolean isCurrentlyRunning(final Job job) {
        final var runningStates = Set.of(JobRunState.SCHEDULED, JobRunState.BUSY);
        return jobService
                .getLastJobRun(jobService.getId(job))
                .map(JobRun::state)
                .map(runningStates::contains)
                .orElse(false);
    }

    @NotNull
    private JobRunContext createContext(
            @NotNull final Job job,
            @Nullable final Integer runId,
            @Nullable final JobRunArguments nullableArguments) {
        final var arguments =
                Objects.requireNonNullElse(nullableArguments, JobRunArguments.DEFAULT);
        return new JobRunContext(
                new JobClassAndId(jobService.getId(job), job.getClass().getCanonicalName()),
                jobService.getOrCreateJobRun(job, runId, arguments),
                arguments,
                jobService.getProperties(job));
    }

    @NotNull
    private JobRunner getJobRunner(
            final JobRunContext jobRunContext, @Nullable final JobRunnerKey jobRunnerKey) {

        final var selectedRunnerKey =
                Objects.requireNonNullElseGet(
                        jobRunnerKey, () -> getRunnerKeyFromJobProperties(jobRunContext));

        if (!jobRunners.containsKey(selectedRunnerKey)) {
            throw new NoSuchElementException(
                    "Could not find a runner for the given runner key " + selectedRunnerKey);
        }

        return jobRunners.get(selectedRunnerKey);
    }

    @NotNull
    private JobRunnerKey getRunnerKeyFromJobProperties(@NotNull final JobRunContext jobRunContext) {
        return jobRunContext.properties().get(JobPropertyDefinitions.RUNNER).value();
    }
}
