package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@Profile("!job-run")
public class JobScheduledStateCheck implements StatusCheck {

    private final JobDataService jobDataService;

    private final Duration maxDurationInScheduledState;

    public JobScheduledStateCheck(
            final JobDataService jobDataService,
            @Value("${ch.ubique.backend.job.checks.max-duration-in-scheduled-state:PT15M}")
                    final Duration maxDurationInScheduledState) {
        this.jobDataService = jobDataService;
        this.maxDurationInScheduledState = maxDurationInScheduledState;
    }

    @Override
    public CheckStatus check() {
        final var scheduledStateForTooLong =
                jobDataService.findJobs().stream()
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
    private Optional<String> scheduledForTooLong(final JobClassAndId job) {
        final var latestRunMaybe = jobDataService.findLatestJobRun(job.id());
        if (latestRunMaybe.isEmpty()) {
            return Optional.empty();
        }

        final var latestRun = latestRunMaybe.get();
        if (latestRun.state() != JobRunState.SCHEDULED) {
            return Optional.empty();
        }

        if (latestRun.runDuration().compareTo(maxDurationInScheduledState) <= 0) {
            return Optional.empty();
        }

        return Optional.of(job.canonicalClassName());
    }
}
