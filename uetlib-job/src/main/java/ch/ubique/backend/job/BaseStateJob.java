package ch.ubique.backend.job;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.*;
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
import java.util.Objects;
import java.util.Optional;

public abstract class BaseStateJob<T> implements Job {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobRunStorage jobRunStorage;
    private final JobDataService jobDataService;
    private final ObjectMapper objectMapper;

    protected BaseStateJob(@NotNull final ApplicationContext applicationContext) {
        jobRunStorage = applicationContext.getBean(JobRunStorage.class);
        jobDataService = applicationContext.getBean(JobDataService.class);
        objectMapper = applicationContext.getBean(ObjectMapper.class);
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

    /**
     * If the state contains generics, you need to overwrite this method to correctly deserialize
     * the saved state.
     */
    @NotNull
    protected TypeReference<T> stateTypeReference() {
        return new TypeReference<>() {};
    }

    @Override
    @NotNull
    public final JobRunState run(@NotNull final JobRunContext jobRunContext) throws Exception {
        logger.info("Getting latest state");
        final var latestState =
                Objects.requireNonNull(
                        getLatestState(jobRunContext), "Latest state must not be null");
        logger.info("Got latest state {}.", latestState);

        if (isAllowedToSkipAndCanSkip(jobRunContext, latestState)) {
            return JobRunState.SKIPPED;
        }

        saveState(latestState, jobRunContext);

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
        if (lastRun.isEmpty()) {
            return null;
        }

        logger.info("Loading previous state.");
        try (final var inputStream =
                jobRunStorage.loadJobRunState(
                        getClass().getSimpleName(), lastRun.get().readableId())) {

            final var previousState = objectMapper.readValue(inputStream, stateTypeReference());
            logger.info("Resolved previous state {}.", previousState);
            return previousState;
        } catch (final IOException e) {
            logger.warn("Could not load previous state.", e);
            return null;
        }
    }
}
