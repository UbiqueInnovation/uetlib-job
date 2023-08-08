package ch.ubique.backend.job.service;

import static java.util.Optional.ofNullable;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.*;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.runners.JobRunner;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JobExecutorService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
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

        logger.info("Executing {}", jobClass);

        final var job = jobService.instantiateJob(jobClass);
        final var context = createContext(job, arguments, runId);
        final var runner = getJobRunner(context, jobRunnerKey);

        logger.info("Running job {} with context {} using runner {}", job, context, runner);
        final var jobState = runner.submit(job, context);
        logger.info("Done. Job state: {}", jobState);

        updateJobRunScheduledOrEnd(context.jobRun(), jobState);
    }

    @NotNull
    private JobRunContext createContext(
            @NotNull final Job job,
            @Nullable final JobRunArguments arguments,
            @Nullable final Integer runId) {
        return new JobRunContext(
                new JobClassAndId(jobService.getId(job), job.getClass().getCanonicalName()),
                jobService.getOrCreateJobRun(job, runId),
                ofNullable(arguments).orElse(JobRunArguments.DEFAULT),
                jobService.getProperties(job));
    }

    @NotNull
    private JobRunner getJobRunner(
            final JobRunContext context, @Nullable final JobRunnerKey jobRunnerKey) {

        final var selectedRunnerKey =
                ofNullable(jobRunnerKey).orElseGet(() -> getRunnerKeyFromJobProperties(context));

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

    private void updateJobRunScheduledOrEnd(final JobRun run, final JobRunState state) {
        final var modifiableJobRun = run.modify();
        modifiableJobRun.state(state);
        if (state != JobRunState.SCHEDULED) {
            modifiableJobRun.finishedAt(Instant.now());
        }
        jobService.updateJobRun(modifiableJobRun.toJobRun());
    }
}
