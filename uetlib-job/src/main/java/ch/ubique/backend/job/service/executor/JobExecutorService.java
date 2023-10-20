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
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
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

        logger.info("{} submitted to executor.", jobClass);

        final var job = jobService.instantiateJob(jobClass);
        final var context = createContext(job, arguments, runId);
        final var runner = getJobRunner(context, jobRunnerKey);

        MDC.put("jobRunId", String.valueOf(context.jobRun().id()));

        final var scheduled = tryToSchedule(runner, job, context);

        if (scheduled) {
            logger.info("Job {} successfully scheduled", job);
        } else {
            logger.error("Job {} could not be scheduled", job);
            jobService.updateJobRun(
                    context.jobRun()
                            .modify()
                            .state(JobRunState.ERROR)
                            .finishedAt(Instant.now())
                            .toJobRun());
        }

        MDC.remove("jobRunId");
    }

    private boolean tryToSchedule(
            final JobRunner runner, final Job job, final JobRunContext context) {
        try {
            if (!runner.isAvailable()) {
                logger.error("The requested runner {} is not available.", runner);
                return false;
            }
            logger.info(
                    "Scheduling job {} with context {} using runner {}",
                    job,
                    context,
                    runner.getClass());
            return runner.submit(job, context);
        } catch (final Exception e) {
            logger.warn("Unhandled exception while trying to schedule job.", e);
            return false;
        }
    }

    @NotNull
    private JobRunContext createContext(
            @NotNull final Job job,
            @Nullable final JobRunArguments arguments,
            @Nullable final Integer runId) {
        return new JobRunContext(
                new JobClassAndId(jobService.getId(job), job.getClass().getCanonicalName()),
                jobService.getOrCreateJobRun(job, runId),
                Objects.requireNonNullElse(arguments, JobRunArguments.DEFAULT),
                jobService.getProperties(job));
    }

    @NotNull
    private JobRunner getJobRunner(
            final JobRunContext context, @Nullable final JobRunnerKey jobRunnerKey) {

        final var selectedRunnerKey =
                Objects.requireNonNullElseGet(
                        jobRunnerKey, () -> getRunnerKeyFromJobProperties(context));

        if (!jobRunners.containsKey(selectedRunnerKey)) {
            throw new NoSuchElementException(
                    "Could not find a runner for the given runner key " + selectedRunnerKey);
        }

        return jobRunners.get(selectedRunnerKey);
    }

    @NotNull
    private JobRunnerKey getRunnerKeyFromJobProperties(@NotNull final JobRunContext context) {
        return context.properties().get(JobPropertyDefinitions.RUNNER).value();
    }
}
