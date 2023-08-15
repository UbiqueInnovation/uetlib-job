package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!job-run")
public class JobLastRunMaxAgeConfiguredCheck implements StatusCheck {

    private final JobDataService jobDataService;

    public JobLastRunMaxAgeConfiguredCheck(final JobDataService jobDataService) {
        this.jobDataService = jobDataService;
    }

    @Override
    public CheckStatus check() {
        final var jobsWithMissingMaxAge =
                jobDataService.findJobs().stream()
                        .filter(this::isMaxAgeMissing)
                        .map(JobClassAndId::canonicalClassName)
                        .toList();

        if (!jobsWithMissingMaxAge.isEmpty()) {
            return new CheckFailed(
                    "The following jobs do not have LAST_RUN_MAX_AGE configured.",
                    jobsWithMissingMaxAge);
        }

        return CheckStatus.CHECK_PASSED;
    }

    private boolean isMaxAgeMissing(final JobClassAndId job) {
        return !jobDataService
                .getProperty(job.id(), JobPropertyDefinitions.LAST_RUN_MAX_AGE)
                .isValuePresent();
    }
}
