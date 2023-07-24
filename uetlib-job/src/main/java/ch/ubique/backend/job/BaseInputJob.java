package ch.ubique.backend.job;

import ch.ubique.backend.core.file.ByteFile;
import ch.ubique.backend.core.service.DataAndInput;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunInput;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.JobRunWithInputs;
import ch.ubique.backend.job.model.entity.JobRunInputEntity;
import ch.ubique.backend.job.storage.JobRunStorage;
import ch.ubique.backend.job.utils.DefaultRetryListener;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.ApplicationContext;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class BaseInputJob<T> extends BaseJob {

    private final JobRunStorage jobRunStorage;

    protected BaseInputJob(@NotNull final ApplicationContext context) {
        super(context);
        jobRunStorage = context.getBean(JobRunStorage.class);
    }

    /**
     * Overriding this method allows skipping a run before {@link BaseInputJob#loadInputs()} is
     * called.
     *
     * @param latestSuccessfulRun non-null info about the last successful run.
     */
    protected boolean hasNewInputs(@NotNull JobRunWithInputs latestSuccessfulRun) {
        return true;
    }

    /**
     * This method should be used to fetch data from all needed input sources.
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
    protected abstract JobRunState processInputs(T data) throws Exception;

    /**
     * Per default, input files are uploaded to the {@link JobRunStorage}. By overwriting this
     * method, you can disable this feature.
     */
    protected boolean isInputFileUploadEnabled() {
        return true;
    }

    @Override
    protected final JobRunState internalRun() throws Exception {

        var allowSkipping = true;

        if (isForceRunEnabled()) {
            logger.info("Force run.");
            allowSkipping = false;
        }

        if (isJobVersionDifferent()) {
            logger.info("The job version differs from the job version of the previous run.");
            allowSkipping = false;
        }

        final var maybeLatestSuccessfulRun = latestSuccessfulRun();
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
        if (allowSkipping && areInputsEqual(data.inputFiles())) {
            logger.info("The inputs did not change. Stopping job run.");
            return JobRunState.SKIPPED;
        }

        saveRunInputsToDatabase(data.inputFiles());
        uploadInputFiles(data.inputFiles());

        return processInputs(data.data());
    }

    private Optional<DataAndInput<T>> tryLoadingInputs() {

        final var retryTemplate =
                RetryTemplate.builder()
                        .maxAttempts(10)
                        .exponentialBackoff(
                                Duration.ofSeconds(2).toMillis(),
                                2,
                                Duration.ofMinutes(5).toMillis())
                        .withListener(new DefaultRetryListener(logger))
                        .build();

        try {
            return retryTemplate.execute(
                    retryContext -> {
                        logger.info("Trying to load data.");
                        final var inputs = loadInputs();
                        logger.info("Successfully loaded data.");
                        return Optional.of(inputs);
                    });
        } catch (final Exception e) {
            logger.warn("Loading data failed", e);
            return Optional.empty();
        }
    }

    private void saveRunInputsToDatabase(final Collection<ByteFile> inputs) {
        for (final var input : inputs) {
            logger.info("Saving input {} to database.", input.getFileName());
            jobDataService.insertJobRunInput(JobRunInputEntity.from(jobRun, input));
        }
    }

    private void uploadInputFiles(final Collection<ByteFile> inputFiles) throws IOException {
        if (!isInputFileUploadEnabled()) {
            return;
        }

        for (final var file : inputFiles) {
            logger.info("Saving job run input file {}.", file.getFileName());
            saveJobRunInput(file, job, jobRun);
        }
    }

    private void saveJobRunInput(final ByteFile file, final Job job, final JobRun jobRun)
            throws IOException {
        jobRunStorage.saveJobRunInput(
                file.getBytes(), job.name(), jobRun.readableId(), file.getFileName());
    }

    private boolean isJobVersionDifferent() {
        final var lastRun = latestSuccessfulRun();
        return lastRun.isEmpty() || version() != lastRun.get().jobVersion();
    }

    private boolean areInputsEqual(final Collection<ByteFile> currentInputs) {
        logger.info("Comparing current inputs to last run");

        if (currentInputs.isEmpty()) {
            logger.info("Job has no inputs => do not compare inputs");
            return false;
        }

        // Get inputs of the latest successful run
        final var latestJobRun = latestSuccessfulRun();
        if (latestJobRun.isEmpty()) {
            logger.info("No previous job run found => Inputs differ");
            return false;
        }

        final Map<String, JobRunInput> latestInputsByName =
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

    private Optional<JobRunWithInputs> latestSuccessfulRun() {
        return jobDataService
                .findLatestJobRunByState(job.id(), JobRunState.DONE)
                .map(
                        run ->
                                new JobRunWithInputs(
                                        run.getId(),
                                        run.getJobVersion(),
                                        run.getState(),
                                        run.getStartedAt(),
                                        run.getStartedAt(),
                                        jobDataService.findJobRunInputs(run.getId()).stream()
                                                .collect(Collectors.toUnmodifiableSet())));
    }
}
