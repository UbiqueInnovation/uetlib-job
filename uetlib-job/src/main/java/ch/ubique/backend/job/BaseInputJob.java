package ch.ubique.backend.job;

import ch.ubique.backend.job.data.service.InputJobDataService;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunInput;
import ch.ubique.backend.job.model.JobRunState;

import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class BaseInputJob<J extends Job, R extends JobRun, I extends JobRunInput>
        extends BaseJob<J, R> {

    private Collection<I> currentInputs;

    private final InputJobDataService<J, R, I> inputJobDataService;

    protected BaseInputJob(InputJobDataService<J, R, I> inputJobDataService) {
        super(inputJobDataService);
        this.inputJobDataService = inputJobDataService;
    }

    protected abstract Collection<I> loadInputs() throws Exception;

    protected abstract JobRunState processInputs();

    @Override
    protected final JobRunState internalRun() {
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

    private Optional<Collection<I>> tryLoadingInputs() {

        final var retryTemplate =
                RetryTemplate.builder()
                        .maxAttempts(10)
                        .exponentialBackoff(
                                Duration.ofSeconds(2).toMillis(),
                                2,
                                Duration.ofMinutes(5).toMillis())
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
        final var lastRun =
                inputJobDataService.findLatestJobRunByState(job.getId(), JobRunState.DONE);
        return lastRun.isEmpty() || version() != lastRun.get().getJobVersion();
    }

    private boolean areInputsEqual() {
        logger.info("Comparing current inputs to last run");

        // Get inputs of the latest successful run
        final var latestJobRun =
                inputJobDataService.findLatestJobRunByState(job.getId(), JobRunState.DONE);
        if (latestJobRun.isEmpty()) {
            logger.info("No previous job run found => Inputs differ");
            return false;
        }

        final var latestInputs = inputJobDataService.findJobRunInputs(latestJobRun.get().getId());
        final var latestInputsByName =
                latestInputs.stream().collect(Collectors.toMap(JobRunInput::getName, i -> i));

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

        for (final var input : currentInputs) {

            final var other = latestInputsByName.get(input.getName());

            if (!input.getHash().equals(other.getHash())) {
                logger.info("Input {} changed since last successful run", input.getName());
                return false;
            }
        }
        return true;
    }
}
