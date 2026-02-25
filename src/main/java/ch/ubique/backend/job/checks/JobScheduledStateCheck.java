package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckLevel;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Profile("!job-run")
public class JobScheduledStateCheck implements StatusCheck {

    private final CheckLevel level;
    private final JobService jobService;

    public JobScheduledStateCheck(
            @Value("${ch.ubique.backend.job.check.scheduled-state:ERROR}") final CheckLevel level,
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
        final var scheduledStateForTooLong =
                jobService.getAllJobs().stream()
                        .map(this::scheduledForTooLong)
                        .flatMap(Optional::stream)
                        .toList();

        if (!scheduledStateForTooLong.isEmpty()) {
            return new CheckFailed(
                    "The following jobs have been in the "
                            + JobRunState.SCHEDULED
                            + " for too long.",
                    scheduledStateForTooLong);
        }

        return CheckStatus.CHECK_PASSED;
    }

    @NotNull
    private Optional<String> scheduledForTooLong(final Job job) {
        final var jobId = jobService.findOrInsertJobByClass(job.getClass()).id();
        final var latestRunMaybe = jobService.getLastJobRun(jobId);
        if (latestRunMaybe.isEmpty()) {
            return Optional.empty();
        }

        final var latestRun = latestRunMaybe.get();
        if (latestRun.latestState() != JobRunState.SCHEDULED) {
            return Optional.empty();
        }

        final var maxDurationInScheduledState =
                jobService
                        .getProperties(job)
                        .get(JobPropertyDefinitions.MAX_DURATION_IN_SCHEDULED_STATE)
                        .value();

        if (latestRun.runDuration().compareTo(maxDurationInScheduledState) <= 0) {
            return Optional.empty();
        }

        return Optional.of(job.getClass().getCanonicalName());
    }
}
