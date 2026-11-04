package ch.uetlib.job.checks;

import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.properties.JobPropertyDefinitions;
import ch.uetlib.job.service.JobService;
import ch.uetlib.monitoring.check.StatusCheck;
import ch.uetlib.monitoring.check.models.CheckFailed;
import ch.uetlib.monitoring.check.models.CheckLevel;
import ch.uetlib.monitoring.check.models.CheckStatus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!job-run")
public class JobLastRunMaxAgeConfiguredCheck implements StatusCheck {

    private final CheckLevel level;
    private final JobService jobService;

    public JobLastRunMaxAgeConfiguredCheck(
            @Value("${ch.ubique.backend.job.check.last-run-max-age-configured:ERROR}")
                    final CheckLevel level,
            final JobService jobService) {
        this.level = level;
        this.jobService = jobService;
    }

    @Override
    public CheckLevel level() {
        return level;
    }

    @Override
    public CheckStatus check() {
        final var jobsWithMissingMaxAge =
                jobService.getEnabledJobClassesWithId().stream()
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
        return !jobService
                .getProperties(job)
                .get(JobPropertyDefinitions.LAST_RUN_MAX_AGE)
                .isValuePresent();
    }
}
