package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.properties.enums.StateCheckStrategy;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckLevel;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public abstract class JobStateCheck implements StatusCheck {

    private final CheckLevel level;
    private final JobRunState stateToCheck;

    private final JobDataService jobDataService;
    private final JobService jobService;

    protected JobStateCheck(
            final CheckLevel level,
            final JobRunState stateToCheck,
            final JobDataService jobDataService,
            final JobService jobService) {
        this.level = level;
        this.stateToCheck = stateToCheck;
        this.jobDataService = jobDataService;
        this.jobService = jobService;
    }

    @Override
    public CheckLevel level() {
        return level;
    }

    @Override
    public final CheckStatus check() {
        final var jobsInStateToCheck =
                jobDataService.findJobs().stream()
                        .filter(this::hasStateToCheck)
                        .map(JobClassAndId::canonicalClassName)
                        .toList();

        if (!jobsInStateToCheck.isEmpty()) {
            return new CheckFailed(
                    "Some jobs are in state " + stateToCheck + ".",
                    Map.of("jobs", jobsInStateToCheck));
        }

        return CheckStatus.CHECK_PASSED;
    }

    private boolean hasStateToCheck(final JobClassAndId job) {
        return switch (stateCheckStrategy(job)) {
            case CHECK_LATEST_SUCCESSFUL -> checkLatestSuccessful(job);
            case CHECK_ALL_SUCCESSFUL -> checkAllSuccessful(job);
            case CHECK_INTERVAL_ALL_SUCCESSFUL -> checkIntervalAllSuccessful(job);
            case CHECK_INTERVAL_SOME_SUCCESSFUL -> checkIntervalSomeSuccessful(job);
        };
    }

    private boolean checkLatestSuccessful(final JobClassAndId job) {
        return jobDataService
                .findLatestJobRunByStates(job.id(), JobRunState.ALL_EXCEPT_RUNNING_STATES)
                .map(run -> run.latestState() == stateToCheck)
                .orElse(false);
    }

    private boolean checkAllSuccessful(final JobClassAndId job) {
        return jobDataService.findLatestJobRunByState(job.id(), stateToCheck).isPresent();
    }

    private boolean checkIntervalAllSuccessful(final JobClassAndId job) {
        final var after = checkIntervalStart(job);
        return jobDataService
                .findAnyJobRunInStatesSince(job.id(), Set.of(stateToCheck), after)
                .isPresent();
    }

    private boolean checkIntervalSomeSuccessful(final JobClassAndId job) {
        final var after = checkIntervalStart(job);
        return jobDataService
                .findAnyJobRunInStatesSince(job.id(), JobRunState.SUCCESS_STATES, after)
                .isEmpty();
    }

    private Instant checkIntervalStart(final JobClassAndId job) {
        final var defaultValue =
                JobPropertyDefinitions.STATE_CHECK_INTERVAL
                        .defaultValue()
                        .orElse(Duration.ofHours(24));
        try {
            return Instant.now()
                    .minus(
                            jobService
                                    .getProperties(job)
                                    .get(JobPropertyDefinitions.STATE_CHECK_INTERVAL)
                                    .valueMaybe()
                                    .orElse(defaultValue));
        } catch (ClassNotFoundException e) {
            return Instant.now().minus(defaultValue);
        }
    }

    private StateCheckStrategy stateCheckStrategy(final JobClassAndId job) {
        final StateCheckStrategy defaultValue =
                JobPropertyDefinitions.STATE_CHECK_STRATEGY
                        .defaultValue()
                        .orElse(StateCheckStrategy.CHECK_LATEST_SUCCESSFUL);
        try {
            return jobService
                    .getProperties(job)
                    .get(JobPropertyDefinitions.STATE_CHECK_STRATEGY)
                    .valueMaybe()
                    .orElse(defaultValue);
        } catch (ClassNotFoundException e) {
            return defaultValue;
        }
    }
}
