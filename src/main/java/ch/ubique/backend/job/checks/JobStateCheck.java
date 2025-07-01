package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckLevel;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import java.util.Map;

public abstract class JobStateCheck implements StatusCheck {

    private final CheckLevel level;
    private final JobRunState stateToCheck;

    private final JobDataService jobDataService;

    protected JobStateCheck(
            final CheckLevel level,
            final JobRunState stateToCheck,
            final JobDataService jobDataService) {
        this.level = level;
        this.stateToCheck = stateToCheck;
        this.jobDataService = jobDataService;
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
        return jobDataService
                .findLatestJobRunByStates(job.id(), JobRunState.ALL_EXCEPT_RUNNING_STATES)
                .map(run -> run.latestState() == stateToCheck)
                .orElse(false);
    }
}
