package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobPropertyKey;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import jakarta.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Service
public class JobTimeoutCheck implements StatusCheck {

    private final JobDataService jobDataService;

    public JobTimeoutCheck(final JobDataService jobDataService) {
        this.jobDataService = jobDataService;
    }

    @Override
    public CheckStatus check() {
        final var errors =
                jobDataService.findJobs().stream()
                        .map(this::timeoutError)
                        .flatMap(Optional::stream)
                        .toList();

        if (!errors.isEmpty()) {
            return new CheckFailed("Some jobs exceeded the run time limit.", errors);
        }

        return CheckStatus.CHECK_PASSED;
    }

    @NotNull
    private Optional<Map<String, Object>> timeoutError(final Job job) {
        final var latestRunMaybe = jobDataService.findLatestJobRun(job.id());
        if (latestRunMaybe.isEmpty()) {
            return Optional.empty();
        }

        final var latestRun = latestRunMaybe.get();
        if (latestRun.getState() != JobRunState.BUSY) {
            return Optional.empty();
        }

        final var runtimeProperty =
                jobDataService.getProperty(job.id(), JobPropertyKey.RUN_TIME_LIMIT);
        final var runtimeLimit = Duration.ofSeconds(Long.parseLong(runtimeProperty));
        if (latestRun.runDuration().toMillis() <= runtimeLimit.toMillis()) {
            return Optional.empty();
        }

        return Optional.of(
                Map.of(
                        "job",
                        job.clazz(),
                        "limit",
                        runtimeLimit,
                        "duration",
                        latestRun.runDuration()));
    }
}
