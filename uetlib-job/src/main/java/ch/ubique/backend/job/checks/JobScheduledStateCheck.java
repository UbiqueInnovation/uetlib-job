package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import jakarta.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class JobScheduledStateCheck implements StatusCheck {

    private final JobDataService jobDataService;

    private static final Duration MAX_DURATION_IN_SCHEDULED_STATE = Duration.ofMinutes(15);

    public JobScheduledStateCheck(final JobDataService jobDataService) {
        this.jobDataService = jobDataService;
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
    private Optional<String> scheduledForTooLong(final Job job) {
        final var latestRunMaybe = jobDataService.findLatestJobRun(job.id());
        if (latestRunMaybe.isEmpty()) {
            return Optional.empty();
        }

        final var latestRun = latestRunMaybe.get();
        if (latestRun.getState() != JobRunState.SCHEDULED) {
            return Optional.empty();
        }

        if (latestRun.runDuration().compareTo(MAX_DURATION_IN_SCHEDULED_STATE) <= 0) {
            return Optional.empty();
        }

        return Optional.of(job.clazz().getCanonicalName());
    }
}
