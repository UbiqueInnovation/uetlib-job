package ch.ubique.backend.job;

import ch.ubique.backend.job.data.service.InputJobDataService;
import ch.ubique.backend.job.model.JobRunInput;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.utils.DefaultRetryListenerSupport;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.retry.support.RetryTemplate;

public abstract class BaseInputJob extends BaseJob {

    private Collection<JobRunInput> currentInputs;

    private final InputJobDataService inputJobDataService;

    protected BaseInputJob(InputJobDataService inputJobDataService) {
        super(inputJobDataService);
        this.inputJobDataService = inputJobDataService;
    }

    protected abstract Collection<JobRunInput> loadInputs() throws Exception;

    @Override
    protected final JobRunState internalRun() throws ExecutionException, InterruptedException {
        final boolean forceRun = jobDataMap.getBoolean("forceRun");

        // Load Inputs
        final var inputs = tryLoadingInputs();
        if (inputs.isEmpty()) {
            logger.error("Could not load inputs. Stopping job run.");
            return JobRunState.ERROR;
        }

        // Check for changes
        currentInputs = inputs.get();
        if (isJobVersionDifferent()) {
            logger.info("The job version differs from the job version of the previous run.");
        } else if (forceRun) {
            logger.info("Forced run. Running even if inputs did not change.");
        } else if (areInputsEqual()) {
            logger.info("The inputs did not change. Stopping job run.");
            return JobRunState.SKIPPED;
        }

        saveRunInputsToDatabase();
        return processInputs();
    }

    protected abstract JobRunState processInputs() throws ExecutionException, InterruptedException;

    private Optional<Collection<JobRunInput>> tryLoadingInputs() {

        final var retryTemplate =
                RetryTemplate.builder()
                        .maxAttempts(10)
                        .exponentialBackoff(
                                Duration.ofSeconds(2).toMillis(),
                                2,
                                Duration.ofMinutes(5).toMillis())
                        .withListener(new DefaultRetryListenerSupport(logger))
                        .build();

        try {
            return retryTemplate.execute(
                    retryContext -> {
                        logger.info("Trying to load data.");
                        final var inputs = loadInputs();
                        logger.info("Successfully loaded data.");
                        return Optional.of(inputs);
                    });
        } catch (Exception e) {
            logger.warn("Loading data failed", e);
            return Optional.empty();
        }
    }

    private void saveRunInputsToDatabase() {
        for (final var runInput : currentInputs) {
            logger.info("Saving input {} to database.", runInput.getName());
            inputJobDataService.insertJobRunInput(runInput);
        }
    }

    private boolean isJobVersionDifferent() {
        final var lastRun = inputJobDataService.findLatestJobRunByState(job.id(), JobRunState.DONE);
        return lastRun.isEmpty() || version() != lastRun.get().getJobVersion();
    }

    private boolean areInputsEqual() {
        logger.info("Comparing current inputs to last run");

        // Get inputs of the latest successful run
        final var latestJobRun =
                inputJobDataService.findLatestJobRunByState(job.id(), JobRunState.DONE);
        if (latestJobRun.isEmpty()) {
            logger.info("No previous job run found => Inputs differ");
            return false;
        }

        final var latestInputs = inputJobDataService.findJobRunInputs(latestJobRun.get().getId());
        final var latestInputsByName =
                latestInputs.stream()
                        .collect(Collectors.toMap(JobRunInput::getName, Function.identity()));

        final var latestInputNames = latestInputsByName.keySet();
        final var currentInputNames =
                currentInputs.stream().map(JobRunInput::getName).collect(Collectors.toSet());

        if (!currentInputNames.equals(latestInputNames)) {
            if (logger.isInfoEnabled()) {
                logger.info(
                        "Input names differ. Latest successful run = {{}}, current run = {{}}",
                        String.join(", ", latestInputNames),
                        String.join(", ", currentInputNames));
            }
            return false;
        }

        return currentInputs.stream()
                .allMatch(
                        input -> {
                            final var other = latestInputsByName.get(input.getName());

                            if (!input.getHash().equals(other.getHash())) {
                                logger.info(
                                        "Input {} changed since last successful run",
                                        input.getName());
                                return false;
                            }
                            return true;
                        });
    }
}
