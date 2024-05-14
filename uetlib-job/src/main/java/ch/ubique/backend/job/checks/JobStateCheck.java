package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import java.util.Map;

public abstract class JobStateCheck implements StatusCheck {

    private final JobRunState stateToCheck;

    private final JobDataService jobDataService;

    protected JobStateCheck(final JobRunState stateToCheck, final JobDataService jobDataService) {
        this.stateToCheck = stateToCheck;
        this.jobDataService = jobDataService;
    }

    @Override
    public final CheckStatus check() {
        final var jobsInStateToCheck =
                jobDataService.findJobs().stream()
                        .filter(this::hasState)
                        .map(JobClassAndId::canonicalClassName)
                        .toList();

        if (!jobsInStateToCheck.isEmpty()) {
            return new CheckFailed(
                    "Some jobs are in the " + stateToCheck + " state.",
                    Map.of("jobs", jobsInStateToCheck));
        }

        return CheckStatus.CHECK_PASSED;
    }

    private boolean hasState(final JobClassAndId job) {
        return jobDataService
                .findLatestJobRunByStates(job.id(), JobRunState.ALL_EXCEPT_RUNNING)
                .map(run -> run.state() == stateToCheck)
                .orElse(false);
    }
}
