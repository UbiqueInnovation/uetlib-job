package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!job-run")
public class JobLastRunMaxAgeConfiguredCheck implements StatusCheck {

    private final JobService jobService;

    public JobLastRunMaxAgeConfiguredCheck(final JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public CheckStatus check() {
        final var jobsWithMissingMaxAge =
                jobService.getAllJobs().stream()
                        .filter(this::isMaxAgeMissing)
                        .map(Job::getClass)
                        .map(Class::getCanonicalName)
                        .toList();

        if (!jobsWithMissingMaxAge.isEmpty()) {
            return new CheckFailed(
                    "The following jobs do not have LAST_RUN_MAX_AGE configured.",
                    jobsWithMissingMaxAge);
        }

        return CheckStatus.CHECK_PASSED;
    }

    private boolean isMaxAgeMissing(final Job job) {
        return !jobService
                .getProperties(job)
                .get(JobPropertyDefinitions.LAST_RUN_MAX_AGE)
                .isValuePresent();
    }
}
