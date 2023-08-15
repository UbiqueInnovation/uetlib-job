package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Profile("!job-run")
public class JobUnfinishedStateCheck implements StatusCheck {

    private final JobDataService jobDataService;

    public JobUnfinishedStateCheck(final JobDataService jobDataService) {
        this.jobDataService = jobDataService;
    }

    @Override
    public CheckStatus check() {
        final var unfinishedJobs =
                jobDataService.findJobs().stream()
                        .filter(this::isUnfinished)
                        .map(JobClassAndId::canonicalClassName)
                        .toList();

        if (!unfinishedJobs.isEmpty()) {
            return new CheckFailed(
                    "Some jobs are unfinished", Map.of("unfinishedJobs", unfinishedJobs));
        }

        return CheckStatus.CHECK_PASSED;
    }

    @NotNull
    private Boolean isUnfinished(final JobClassAndId job) {
        return jobDataService
                .findLatestJobRunByStates(job.id(), JobRunState.allExceptRunning())
                .map(run -> run.state() == JobRunState.UNFINISHED)
                .orElse(false);
    }
}
