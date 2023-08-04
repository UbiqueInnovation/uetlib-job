package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import jakarta.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

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
    private Optional<Map<String, Object>> timeoutError(final JobClassAndId job) {
        final var latestRunMaybe = jobDataService.findLatestJobRun(job.id());
        if (latestRunMaybe.isEmpty()) {
            return Optional.empty();
        }

        final var latestRun = latestRunMaybe.get();
        if (latestRun.state() != JobRunState.BUSY) {
            return Optional.empty();
        }

        final var runtimeLimit =
                jobDataService.getProperty(job.id(), JobPropertyDefinitions.RUN_TIME_LIMIT).value();

        if (latestRun.runDuration().compareTo(runtimeLimit) <= 0) {
            return Optional.empty();
        }

        return Optional.of(
                Map.of(
                        "job",
                        job.canonicalClassName(),
                        "limit",
                        runtimeLimit,
                        "duration",
                        latestRun.runDuration()));
    }
}
