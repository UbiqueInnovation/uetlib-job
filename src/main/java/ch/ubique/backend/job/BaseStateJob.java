package ch.ubique.backend.job;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import ch.ubique.backend.core.io.InputStreamProvider;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.files.JobFile;
import ch.ubique.backend.job.files.JobRunFileProvider;
import ch.ubique.backend.job.model.*;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.executor.JobExecutorService;
import ch.ubique.backend.job.storage.JobRunStorage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public abstract class BaseStateJob<T> implements Job, JobRunFileProvider {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobRunStorage jobRunStorage;
    private final JobDataService jobDataService;
    private final ObjectMapper objectMapper;
    private final JobExecutorService jobExecutorService;

    protected BaseStateJob(@NotNull final ApplicationContext applicationContext) {
        jobRunStorage = applicationContext.getBean(JobRunStorage.class);
        jobDataService = applicationContext.getBean(JobDataService.class);
        objectMapper = applicationContext.getBean(ObjectMapper.class);
        jobExecutorService = applicationContext.getBean(JobExecutorService.class);
    }

    @Override
    public boolean supportsForceRun() {
        return true;
    }

    /**
     * This method should be used to fetch/compute the latest state.
     *
     * <p>Includes retries with exponential backoff.
     *
     * @return the state want to pass to the {@link BaseStateJob#processState} method.
     * @throws Exception if anything goes wrong, the job will stop and report an error.
     */
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 10,
            backoff = @Backoff(maxDelay = 300_000, multiplier = 2.0),
            listeners = "defaultRetryListener")
    @NotNull
    protected abstract T getLatestState(JobRunContext jobRunContext) throws Exception;

    /**
     * This method is called as recovery with the thrown exception if {@link #getLatestState}} still
     * throws an exception after maxAttempts and backoff have run out.
     */
    @Recover
    protected T getLatestStateRecovery(final Exception e) throws Exception {
        logger.warn("Loading data failed even after retry and backoff.");
        throw e;
    }

    /**
     * This method determines whether the job can be skipped based on the previous and latest state.
     *
     * <p>By default this method returns true if the state changed. You can change the behaviour by
     * overriding this method.
     *
     * @param latestState the latestState returned by {@link BaseStateJob#getLatestState}
     */
    protected boolean canSkip(@Nullable final T previousState, final T latestState) {
        logger.info("Checking if latest state differs from previous state.");
        if (Objects.equals(previousState, latestState)) {
            logger.info("Latest state is equal to previous state.");
            return true;
        }

        logger.info("Latest state is different from previous state.");
        return false;
    }

    /**
     * This method is called if the job has a new latestState.
     *
     * @param latestState the latestState returned by {@link BaseStateJob#getLatestState}
     * @throws Exception if anything goes wrong, the job will stop and report an error.
     */
    @NotNull
    protected abstract JobRunState processState(T latestState, @NotNull JobRunContext jobRunContext)
            throws Exception;

    /** You need to overwrite this method to correctly deserialize the saved state. */
    @NotNull
    protected abstract TypeReference<T> stateTypeReference();

    /**
     * You may override this method if you want the processing to run on a specific runner. This
     * runner is allowed to differ from the runner specified in the job properties.
     */
    @Nullable
    protected JobRunnerKey runnerForProcessing() {
        return null;
    }

    @Override
    public Collection<JobFile> jobRunFiles(final JobRun jobRun) {
        final boolean hasStateJson =
                jobDataService
                        .findJobRunById(jobRun.id())
                        .map(JobRun::latestState)
                        .map(state -> state != JobRunState.SKIPPED && hasStateForRun(jobRun))
                        .orElse(false);
        if (!hasStateJson) {
            return List.of();
        }
        return List.of(
                new JobFile(
                        "state.json", InputStreamProvider.of(() -> getStateInputStream(jobRun))));
    }

    @Override
    @NotNull
    public final JobRunState run(@NotNull final JobRunContext jobRunContext) throws Exception {
        final var currentJobRun = jobRunContext.jobRun();

        // Check if state for current run has already been computed and saved.
        final var currentState = loadStateIfPresent(currentJobRun);
        if (currentState != null) {
            logger.info("Starting processing for state {}.", currentState);
            return processState(currentState, jobRunContext);
        }

        // Compute latest state
        logger.info("Computing latest state.");
        final var latestState =
                requireNonNull(getLatestState(jobRunContext), "Latest state must not be null");
        logger.info("Got latest state {}.", latestState);

        // Skip if allowed
        if (isAllowedToSkipAndCanSkip(jobRunContext, latestState)) {
            return JobRunState.SKIPPED;
        }

        // Save state to storage
        saveState(latestState, jobRunContext);

        // Run processing on a different runner, if requested
        final var currentRunnerKey = currentJobRun.runnerKey();
        final var processingRunnerKey = requireNonNullElse(runnerForProcessing(), currentRunnerKey);
        if (currentRunnerKey != processingRunnerKey) {
            logger.info("Requested to run processing on runner {}.", processingRunnerKey);
            jobExecutorService.submit(
                    getClass(), jobRunContext.arguments(), currentJobRun.id(), processingRunnerKey);
            return JobRunState.SCHEDULED;
        }

        // Start processing on current runner
        logger.info("Starting processing.");
        return processState(latestState, jobRunContext);
    }

    private boolean isAllowedToSkipAndCanSkip(
            final JobRunContext jobRunContext, final T latestState) {

        if (!allowSkipping(jobRunContext)) {
            logger.info("Skipping is not allowed.");
            return false;
        }

        logger.info("Job is allowed to skip this run.");
        final var previousState = loadPreviousState(jobRunContext);

        if (!canSkip(previousState, latestState)) {
            logger.info("Job run can not be skipped.");
            return false;
        }

        logger.info("Job run will be skipped.");
        return true;
    }

    private boolean allowSkipping(final JobRunContext jobRunContext) {
        if (jobRunContext.arguments().forceRun()) {
            logger.info("Force run.");
            return false;
        }

        if (latestSuccessfulRun(jobRunContext.job()).isEmpty()) {
            logger.info("No successful runs so far.");
            return false;
        }

        if (isJobVersionDifferent(jobRunContext.job())) {
            logger.info("The job version differs from the job version of the previous run.");
            return false;
        }

        return true;
    }

    private boolean isJobVersionDifferent(final JobClassAndId job) {
        final var lastVersion = latestSuccessfulRun(job).map(JobRun::jobVersion).orElse(null);
        return !Objects.equals(version(), lastVersion);
    }

    private Optional<JobRun> latestSuccessfulRun(final JobClassAndId job) {
        return jobDataService.findLatestJobRunByState(job.id(), JobRunState.DONE);
    }

    private void saveState(final T state, final JobRunContext jobRunContext) throws IOException {
        logger.info("Saving state {}.", state);
        final var readableId = jobRunContext.jobRun().readableId();
        jobRunStorage.saveJobRunState(state, getClass().getSimpleName(), readableId);
    }

    @Nullable
    private T loadPreviousState(final JobRunContext jobRunContext) {
        final var lastRun = latestSuccessfulRun(jobRunContext.job());
        return lastRun.map(this::loadState).orElse(null);
    }

    @Nullable
    private T loadState(@NotNull final JobRun jobRun) {
        logger.info("Loading state of run {}.", jobRun.id());
        try (final var inputStream = getStateInputStream(jobRun)) {
            final var previousState = objectMapper.readValue(inputStream, stateTypeReference());
            logger.info("Resolved state {}.", previousState);
            return previousState;
        } catch (final Exception e) {
            logger.warn("Could not load state for run {}.", jobRun.id(), e);
            return null;
        }
    }

    private InputStream getStateInputStream(final JobRun jobRun) throws IOException {
        return jobRunStorage.loadJobRunState(getClass().getSimpleName(), jobRun.readableId());
    }

    @Nullable
    private T loadStateIfPresent(@NotNull final JobRun jobRun) {
        if (!hasStateForRun(jobRun)) {
            return null;
        }
        return loadState(jobRun);
    }

    private boolean hasStateForRun(final JobRun jobRun) {
        try {
            return jobRunStorage.jobRunStateExists(getClass().getSimpleName(), jobRun.readableId());
        } catch (final IOException e) {
            logger.warn("Could not query if state for run {} exists.", jobRun.id(), e);
            return false;
        }
    }
}
