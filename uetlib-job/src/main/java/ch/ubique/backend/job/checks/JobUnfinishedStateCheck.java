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
                        .map(Job::clazz)
                        .toList();

        if (!unfinishedJobs.isEmpty()) {
            return new CheckFailed(
                    "Some jobs are unfinished", Map.of("unfinishedJobs", unfinishedJobs));
        }

        return CheckStatus.CHECK_PASSED;
    }

    @NotNull
    private Boolean isUnfinished(final Job job) {
        return jobDataService
                .findLatestJobRun(job.id())
                .map(run -> run.getState() == JobRunState.UNFINISHED)
                .orElse(false);
    }
}
