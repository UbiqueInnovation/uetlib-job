package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import jakarta.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class JobErrorStateCheck implements StatusCheck {

    private final JobDataService jobDataService;

    public JobErrorStateCheck(final JobDataService jobDataService) {
        this.jobDataService = jobDataService;
    }

    @Override
    public CheckStatus check() {
        final var jobsWithError =
                jobDataService.findJobs().stream()
                        .filter(this::hasError)
                        .map(JobClassAndId::canonicalClassName)
                        .toList();

        if (!jobsWithError.isEmpty()) {
            return new CheckFailed("Some jobs have errors", Map.of("jobsWithError", jobsWithError));
        }

        return CheckStatus.CHECK_PASSED;
    }

    @NotNull
    private Boolean hasError(final JobClassAndId job) {
        return jobDataService
                .findLatestJobRunByStates(job.id(), JobRunState.allExceptRunning())
                .map(run -> run.state() == JobRunState.ERROR)
                .orElse(false);
    }
}
