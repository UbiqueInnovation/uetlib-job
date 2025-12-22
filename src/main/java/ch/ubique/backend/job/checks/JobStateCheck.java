package ch.ubique.backend.job.checks;

import static ch.ubique.backend.job.model.properties.JobPropertyDefinitions.STATE_CHECK_INTERVAL;
import static ch.ubique.backend.job.model.properties.JobPropertyDefinitions.STATE_CHECK_STRATEGY;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.enums.StateCheckStrategy;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckLevel;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

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
        final var failedJobs =
                jobDataService.findJobs().stream()
                        .filter(this::checkJobFailed)
                        .map(JobClassAndId::canonicalClassName)
                        .toList();

        if (!failedJobs.isEmpty()) {
            return new CheckFailed(
                    "Some jobs are in state " + stateToCheck + ".", Map.of("jobs", failedJobs));
        }

        return CheckStatus.CHECK_PASSED;
    }

    private boolean checkJobFailed(final JobClassAndId job) {
        return switch (stateCheckStrategy(job)) {
            case CHECK_LATEST_SUCCESSFUL -> checkLatestRunFailed(job);
            case CHECK_ALL_SUCCESSFUL -> checkSomeRunFailed(job);
            case CHECK_INTERVAL_ALL_SUCCESSFUL -> checkSomeRunInIntervalFailed(job);
            case CHECK_INTERVAL_SOME_SUCCESSFUL -> checkNoRunInIntervalSuccessful(job);
        };
    }

    private boolean checkLatestRunFailed(final JobClassAndId job) {
        return jobDataService
                .findLatestJobRunByStates(job.id(), JobRunState.ALL_EXCEPT_RUNNING_STATES)
                .map(run -> run.latestState() == stateToCheck)
                .orElse(false);
    }

    private boolean checkSomeRunFailed(final JobClassAndId job) {
        return jobDataService.findLatestJobRunByState(job.id(), stateToCheck).isPresent();
    }

    private boolean checkSomeRunInIntervalFailed(final JobClassAndId job) {
        final var after = checkIntervalStart(job);
        return jobDataService
                .findAnyJobRunInStatesSince(job.id(), Set.of(stateToCheck), after)
                .isPresent();
    }

    private boolean checkNoRunInIntervalSuccessful(final JobClassAndId job) {
        final var after = checkIntervalStart(job);
        return jobDataService
                .findAnyJobRunInStatesSince(job.id(), JobRunState.SUCCESS_STATES, after)
                .isEmpty();
    }

    private Instant checkIntervalStart(final JobClassAndId job) {
        final var checkInterval =
                jobService
                        .tryToInstantiateJob(job.id())
                        .map(jobService::getProperties)
                        .map(p -> p.getValue(STATE_CHECK_INTERVAL))
                        .orElse(STATE_CHECK_INTERVAL.defaultValue().orElseThrow());
        return Instant.now().minus(checkInterval);
    }

    private StateCheckStrategy stateCheckStrategy(final JobClassAndId job) {
        return jobService
                .tryToInstantiateJob(job.id())
                .map(jobService::getProperties)
                .map(p -> p.getValue(STATE_CHECK_STRATEGY))
                .orElse(STATE_CHECK_STRATEGY.defaultValue().orElseThrow());
    }
}
