package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckStatus;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class JobErrorStateCheck implements StatusCheck {

    private final JobDataService jobDataService;

    public JobErrorStateCheck(final JobDataService jobDataService) {
        this.jobDataService = jobDataService;
    }

    @Override
    public CheckStatus check() {
        final var jobsWithError =
                jobDataService.findJobs().stream().filter(this::hasError).map(Job::clazz).toList();

        if (!jobsWithError.isEmpty()) {
            return new CheckFailed("Some jobs have errors", Map.of("jobsWithError", jobsWithError));
        }

        return CheckStatus.CHECK_PASSED;
    }

    @NotNull
    private Boolean hasError(final Job job) {
        return jobDataService
                .findLatestJobRun(job.id())
                .map(run -> run.getState() == JobRunState.ERROR)
                .orElse(false);
    }
}
