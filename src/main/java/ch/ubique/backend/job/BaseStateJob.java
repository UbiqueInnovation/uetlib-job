package ch.ubique.backend.job;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.*;
import ch.ubique.backend.job.model.executer.SuccessfulSubmissionResult;
import ch.ubique.backend.job.model.executer.UnsuccessfulSubmissionResult;
import ch.ubique.backend.job.model.input.JobRunInputDeclaration;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.executor.JobExecutorService;
import ch.ubique.backend.job.service.inputs.JobRunInputManager;
import ch.ubique.backend.storage.output.JsonOutput;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

public abstract class BaseStateJob<T> implements Job {

    protected static final String STATE_PATH = "state.json";

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobDataService jobDataService;
    private final JobRunInputManager jobRunInputManager;
    private final JsonMapper jsonMapper;
    private final ApplicationContext applicationContext;

    protected BaseStateJob(final ApplicationContext applicationContext) {
        jobDataService = applicationContext.getBean(JobDataService.class);
        jsonMapper = applicationContext.getBean(JsonMapper.class);
        jobRunInputManager = applicationContext.getBean(JobRunInputManager.class);
        this.applicationContext = applicationContext;
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
    @SuppressWarnings("java:S112")
    protected abstract T getLatestState(JobRunContext jobRunContext) throws Exception;

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
    @SuppressWarnings("java:S112")
    protected abstract JobRunState processState(T latestState, JobRunContext jobRunContext)
            throws Exception;

    /** You need to overwrite this method to correctly deserialize the saved state. */
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
    public final JobRunState run(final JobRunContext jobRunContext) throws Exception {
        final var currentJobRun = jobRunContext.jobRun();

        // Check if state for current run has already been computed and saved.
        final var currentState = loadStateIfPresent(currentJobRun);
        if (currentState != null) {
            logger.info("Loaded state, starting processing.");
            return processState(currentState, jobRunContext);
        }

        // Compute latest state
        logger.debug("Computing latest state.");
        final var latestState =
                requireNonNull(getLatestState(jobRunContext), "Latest state must not be null");
        logger.debug("Got latest state {}.", latestState);

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
            final var submission =
                    applicationContext
                            .getBean(JobExecutorService.class)
                            .submit(
                                    getClass(),
                                    jobRunContext.arguments(),
                                    currentJobRun.id(),
                                    processingRunnerKey);
            return switch (submission) {
                case final SuccessfulSubmissionResult ignored -> JobRunState.SCHEDULED;
                case UnsuccessfulSubmissionResult(final var status) ->
                        switch (status) {
                            case SUBMITTED -> JobRunState.SCHEDULED;
                            case FAILED, DISALLOWED -> {
                                logger.info(
                                        "Could not schedule job {}: {}",
                                        getClass().getCanonicalName(),
                                        status);
                                yield JobRunState.SCHEDULING_FAILED;
                            }
                        };
            };
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

        logger.debug("Job is allowed to skip this run.");
        final var previousState = loadPreviousState(jobRunContext);

        if (!canSkip(previousState, latestState)) {
            logger.info("Based on the current state, the job run can not be skipped.");
            return false;
        }

        logger.info("Job run will be skipped.");
        return true;
    }

    private boolean allowSkipping(final JobRunContext jobRunContext) {
        if (jobRunContext.arguments().forceRun()) {
            logger.debug("Force run.");
            return false;
        }

        if (latestSuccessfulRun(jobRunContext.job()).isEmpty()) {
            logger.debug("No successful runs so far.");
            return false;
        }

        return true;
    }

    private Optional<JobRun> latestSuccessfulRun(final JobClassAndId job) {
        return jobDataService.findLatestJobRunByState(job.id(), JobRunState.DONE);
    }

    private void saveState(final T state, final JobRunContext jobRunContext) throws IOException {
        logger.debug("Saving state {}.", state);
        final var inputDeclaration = stateInputDeclaration(jobRunContext.jobRun());
        final var output = new JsonOutput<>(state, jsonMapper);
        jobRunInputManager.save(inputDeclaration, output);
    }

    @Nullable
    private T loadPreviousState(final JobRunContext jobRunContext) {
        final var lastRun = latestSuccessfulRun(jobRunContext.job());
        return lastRun.map(this::loadState).orElse(null);
    }

    @Nullable
    private T loadState(final JobRun jobRun) {
        logger.debug("Loading state of run {}.", jobRun.id());
        try (final var inputStream = getStateInputStream(jobRun)) {
            final var previousState = jsonMapper.readValue(inputStream, stateTypeReference());
            logger.debug("Resolved state {}.", previousState);
            return previousState;
        } catch (final JacksonException e) {
            logger.info("State json could not be parsed. Did the model change?", e);
            return null;
        } catch (final Exception e) {
            logger.warn("Could not load state for run {}.", jobRun.id(), e);
            return null;
        }
    }

    private InputStream getStateInputStream(final JobRun jobRun) throws IOException {
        return jobRunInputManager.getJobRunInput(stateInputDeclaration(jobRun));
    }

    @Nullable
    private T loadStateIfPresent(final JobRun jobRun) {
        if (!hasStateForRun(jobRun)) {
            return null;
        }
        return loadState(jobRun);
    }

    private boolean hasStateForRun(final JobRun jobRun) {
        try {
            return jobRunInputManager.exists(stateInputDeclaration(jobRun));
        } catch (final IOException e) {
            logger.warn("Could not query if state for run {} exists.", jobRun.id(), e);
            return false;
        }
    }

    private JobRunInputDeclaration stateInputDeclaration(final JobRun jobRun) {
        return new JobRunInputDeclaration(jobRun.id(), STATE_PATH);
    }
}
