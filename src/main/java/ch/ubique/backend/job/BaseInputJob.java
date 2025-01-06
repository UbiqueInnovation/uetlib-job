package ch.ubique.backend.job;

import ch.ubique.backend.core.file.ByteFile;
import ch.ubique.backend.core.service.DataAndInput;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.files.JobFile;
import ch.ubique.backend.job.files.JobRunFileProvider;
import ch.ubique.backend.job.files.JobRunInputManager;
import ch.ubique.backend.job.model.*;
import ch.ubique.backend.job.model.input.JobRunInputDeclaration;
import ch.ubique.backend.job.storage.JobRunStorage;
import ch.ubique.backend.storage.output.ByteOutput;

import com.pivovarit.function.exception.WrappedException;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class BaseInputJob<T> implements Job, JobRunFileProvider {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobDataService jobDataService;
    private final JobRunInputManager jobRunInputManager;

    private JobRunContext jobRunContext;

    protected BaseInputJob(@NotNull final ApplicationContext applicationContext) {
        jobDataService = applicationContext.getBean(JobDataService.class);
        jobRunInputManager = applicationContext.getBean(JobRunInputManager.class);
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
    protected boolean hasNewInputs(@NotNull final JobRunWithInputs latestSuccessfulRun) {
        return true;
    }

    /**
     * This method should be used to fetch data from all needed input sources. Includes retries with
     * exponential backoff:
     *
     * <p>min delay 2 seconds, max delay 5 minutes with random delays
     *
     * @return the data you want to pass to the {@link BaseInputJob#processInputs} method.
     * @throws Exception if anything goes wrong, the job will stop and report an error.
     */
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 10,
            backoff = @Backoff(maxDelay = 300_000, multiplier = 2.0),
            listeners = "defaultRetryListener")
    protected abstract DataAndInput<T> loadInputs() throws Exception;

    /**
     * This method is called as recovery with the thrown exception if {@link #loadInputs()} still
     * throws an exception after maxAttempts and backoff have run out.
     *
     * <p>The return type and all arguments after the Exception must match with the method to
     * recover from.
     */
    @Recover
    protected DataAndInput<T> loadInputsRecovery(final Exception e) {
        logger.warn("Loading data failed even after retry and backoff:", e);
        return null;
    }

    @Override
    public Collection<JobFile> jobRunFiles(final JobRun jobRun) {
        if (!isInputFileUploadEnabled()) {
            return List.of();
        }
        return jobRunInputManager.getAllJobRunFiles(jobRun.id());
    }

    /**
     * This method is called once the data is loaded and can be used to consume the input data.
     *
     * @param data the data returned by {@link BaseInputJob#loadInputs()}
     * @throws Exception if anything goes wrong, the job will stop and report an error.
     */
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

    private Optional<DataAndInput<T>> tryLoadingInputs() {
        try {
            return Optional.ofNullable(loadInputs());
        } catch (final Exception e) {
            logger.error(
                    "should not land here, loadInputsRecovery should have caught the error and"
                            + " recovered from the exception in loadInputs()");
            throw new WrappedException(e);
        }
    }

    private void saveRunInputsToDatabase(final Collection<ByteFile> inputs, final JobRun jobRun) {
        inputs.stream()
                .map(input -> JobRunInputDeclaration.from(jobRun, input))
                .forEach(jobRunInputManager::saveToDatabase);
    }

    private void uploadInputFiles(final Collection<ByteFile> inputFiles, final JobRun jobRun)
            throws IOException {
        if (!isInputFileUploadEnabled()) {
            return;
        }

        for (final var file : inputFiles) {
            logger.info("Saving job run input file {}.", file.getFileName());
            jobRunInputManager.saveToStorage(
                    JobRunInputDeclaration.from(jobRun, file), new ByteOutput(file.getBytes()));
        }
    }

    private boolean isJobVersionDifferent(final JobClassAndId job) {
        final var lastRun = latestSuccessfulRun(job);
        return lastRun.isEmpty() || version() != lastRun.get().jobVersion();
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

                            if (!input.getFileHash().equals(other.getHash())) {
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
                                        run.jobVersion(),
                                        run.latestState(),
                                        run.startedAt(),
                                        run.startedAt(),
                                        jobDataService.findJobRunInputs(run.id()).stream()
                                                .collect(Collectors.toUnmodifiableSet())));
    }
}
