package ch.ubique.uetlib.job;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import ch.ubique.uetlib.core.file.ByteFile;
import ch.ubique.uetlib.core.service.DataAndInput;
import ch.ubique.uetlib.core.utils.retry.LoggingRetryListener;
import ch.ubique.uetlib.job.data.service.JobDataService;
import ch.ubique.uetlib.job.model.*;
import ch.ubique.uetlib.job.model.input.JobRunInputDeclaration;
import ch.ubique.uetlib.job.service.inputs.JobRunInputManager;
import ch.ubique.uetlib.job.storage.JobRunStorage;
import ch.ubique.uetlib.storage.output.ByteOutput;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.exception.UncheckedException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

public abstract class BaseInputJob<T> implements Job {

    private static final RetryTemplate DEFAULT_RETRY_TEMPLATE;

    static {
        DEFAULT_RETRY_TEMPLATE =
                new RetryTemplate(
                        RetryPolicy.builder()
                                .includes(Exception.class)
                                .maxRetries(9)
                                .delay(Duration.ofSeconds(2))
                                .maxDelay(Duration.ofMinutes(5))
                                .multiplier(2)
                                .jitter(Duration.ofSeconds(1))
                                .build());
        DEFAULT_RETRY_TEMPLATE.setRetryListener(new LoggingRetryListener());
    }

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final String version;
    private final JobDataService jobDataService;
    private final JobRunInputManager jobRunInputManager;
    private final RetryTemplate retryTemplate;

    private @Nullable JobRunContext jobRunContext;

    protected BaseInputJob(final ApplicationContext applicationContext) {
        jobDataService = applicationContext.getBean(JobDataService.class);
        jobRunInputManager = applicationContext.getBean(JobRunInputManager.class);
        version =
                requireNonNullElse(
                        applicationContext.getBean(BuildProperties.class).getVersion(), "unknown");

        this.retryTemplate = DEFAULT_RETRY_TEMPLATE;
    }

    @Override
    public boolean supportsForceRun() {
        return true;
    }

    /**
     * Overriding this method allows skipping a run before {@link BaseInputJob#loadInputs()} is
     * called.
     *
     * @param latestSuccessfulRun non-null info about the last successful run.
     */
    protected boolean hasNewInputs(final JobRunWithInputs latestSuccessfulRun) {
        return true;
    }

    protected final DataAndInput<T> loadInputsWithRetry() {
        try {
            return retryTemplate().execute(this::loadInputs);
        } catch (final RetryException e) {
            logger.error("Could not load inputs even after retries and backoff.", e);
            throw new RuntimeException(e.getCause());
        }
    }

    /**
     * This method should be used to fetch data from all needed input sources. Includes retries with
     * exponential backoff:
     *
     * <p>min delay 2 seconds, max delay 5 minutes with random delays
     *
     * <p>The {@link RetryTemplate} used can we customised by overriding {@link #retryTemplate()}
     *
     * @return the data you want to pass to the {@link BaseInputJob#processInputs} method.
     * @throws Exception if anything goes wrong, the job will stop and report an error.
     */
    protected abstract DataAndInput<T> loadInputs() throws Exception;

    /**
     * This method is called once the data is loaded and can be used to consume the input data.
     *
     * @param data the data returned by {@link BaseInputJob#loadInputs()}
     * @throws Exception if anything goes wrong, the job will stop and report an error.
     */
    @SuppressWarnings("java:S112")
    protected abstract JobRunState processInputs(T data) throws Exception;

    /**
     * Per default, input files are uploaded to the {@link JobRunStorage}. By overwriting this
     * method, you can disable this feature.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean isInputFileUploadEnabled() {
        return true;
    }

    /**
     * Access to job run context during execution of the job. Use for example to access the forceRun
     * flag in {@link #processInputs}.
     */
    protected JobRunContext jobRunContext() {
        if (jobRunContext == null) {
            throw new IllegalStateException(
                    "This method can only be called during execution of the BaseInputJob::run"
                            + " method");
        }
        return jobRunContext;
    }

    @Override
    public final JobRunState run(final JobRunContext jobRunContext) throws Exception {
        this.jobRunContext = jobRunContext;

        var allowSkipping = true;

        if (jobRunContext.arguments().forceRun()) {
            logger.info("Force run.");
            allowSkipping = false;
        }

        if (isJobVersionDifferent(jobRunContext.job())) {
            logger.info("The job version differs from the job version of the previous run.");
            allowSkipping = false;
        }

        final var maybeLatestSuccessfulRun = latestSuccessfulRun(jobRunContext.job());
        if (maybeLatestSuccessfulRun.isEmpty()) {
            logger.info("No successful runs so far.");
            allowSkipping = false;
        }

        if (!allowSkipping) {
            logger.info("Job cannot be skipped.");
        }

        if (allowSkipping && !hasNewInputs(maybeLatestSuccessfulRun.get())) {
            logger.info("hasNewInputs() returned false. Stopping job run.");
            return JobRunState.SKIPPED;
        }

        // Load Inputs
        final var dataMaybe = tryLoadingInputs();
        if (dataMaybe.isEmpty()) {
            logger.error("Could not load inputs. Stopping job run.");
            return JobRunState.ERROR;
        }

        // Check for changes
        final var data = dataMaybe.get();
        if (allowSkipping && areInputsEqual(data.inputFiles(), jobRunContext.job())) {
            logger.info("The inputs did not change. Stopping job run.");
            return JobRunState.SKIPPED;
        }

        saveRunInputsToDatabase(data.inputFiles(), jobRunContext.jobRun());
        uploadInputFiles(data.inputFiles(), jobRunContext.jobRun());

        final var jobRunState = processInputs(data.data());

        this.jobRunContext = null;

        return jobRunState;
    }

    protected RetryTemplate retryTemplate() {
        return retryTemplate;
    }

    private Optional<DataAndInput<T>> tryLoadingInputs() {
        try {
            return Optional.ofNullable(loadInputsWithRetry());
        } catch (final Exception e) {
            logger.error(
                    "should not land here, loadInputsRecovery should have caught the error and"
                            + " recovered from the exception in loadInputs()");
            throw new UncheckedException(e);
        }
    }

    private void saveRunInputsToDatabase(final Collection<ByteFile> inputs, final JobRun jobRun) {
        jobRunInputManager.saveToDatabase(
                inputs.stream().map(input -> JobRunInputDeclaration.from(jobRun, input)).toList());
    }

    private void uploadInputFiles(final Collection<ByteFile> inputFiles, final JobRun jobRun)
            throws IOException {
        if (!isInputFileUploadEnabled()) {
            return;
        }

        for (final var file : inputFiles) {
            logger.info("Saving job run input file {}.", file.getFileName());
            jobRunInputManager.saveToStorage(
                    JobRunInputDeclaration.from(jobRun, file),
                    new ByteOutput(requireNonNull(file.getBytes())));
        }
    }

    private boolean isJobVersionDifferent(final JobClassAndId job) {
        final var lastRun = latestSuccessfulRun(job);
        return lastRun.isEmpty() || !version.equals(lastRun.get().versionTag());
    }

    private boolean areInputsEqual(
            final Collection<ByteFile> currentInputs, final JobClassAndId job) {
        logger.info("Comparing current inputs to last run");

        if (currentInputs.isEmpty()) {
            logger.info("Job has no inputs => do not compare inputs");
            return false;
        }

        // Get inputs of the latest successful run
        final var latestJobRun = latestSuccessfulRun(job);
        if (latestJobRun.isEmpty()) {
            logger.info("No previous job run found => Inputs differ");
            return false;
        }

        final var latestInputsByName =
                latestJobRun.get().inputs().stream()
                        .collect(
                                Collectors.toMap(
                                        JobRunInput::getName,
                                        Function.identity(),
                                        (existing, other) -> existing));

        final var latestInputNames = latestInputsByName.keySet();
        final var currentInputNames =
                currentInputs.stream().map(ByteFile::getFileName).collect(Collectors.toSet());

        if (!currentInputNames.equals(latestInputNames)) {
            if (logger.isInfoEnabled()) {
                logger.info(
                        "Input names differ. Latest successful run = {}, current run = {}",
                        latestInputNames,
                        currentInputNames);
            }
            return false;
        }

        return currentInputs.stream()
                .allMatch(
                        input -> {
                            final var other = latestInputsByName.get(input.getFileName());

                            if (other == null
                                    || !requireNonNull(input.getFileHash())
                                            .equals(other.getHash())) {
                                logger.info(
                                        "Input {} changed since last successful run",
                                        input.getFileName());
                                return false;
                            }
                            return true;
                        });
    }

    private Optional<JobRunWithInputs> latestSuccessfulRun(final JobClassAndId job) {
        return jobDataService
                .findLatestJobRunByState(job.id(), JobRunState.DONE)
                .map(
                        run ->
                                new JobRunWithInputs(
                                        run.id(),
                                        run.versionTag(),
                                        run.latestState(),
                                        run.startedAt(),
                                        run.startedAt(),
                                        jobDataService.findJobRunInputs(run.id()).stream()
                                                .collect(Collectors.toUnmodifiableSet())));
    }
}
