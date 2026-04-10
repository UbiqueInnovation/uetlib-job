package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckLevel;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Service
@Profile("!job-run")
public class JobTimeoutCheck implements StatusCheck {

    private final CheckLevel level;
    private final JobService jobService;
    private final JobDataService jobDataService;

    public JobTimeoutCheck(
            @Value("${ch.ubique.backend.job.check.timeout:ERROR}") final CheckLevel level,
            final JobService jobService,
            final JobDataService jobDataService) {
        this.level = level;
        this.jobService = jobService;
        this.jobDataService = jobDataService;
    }

    @Override
    public CheckLevel level() {
        return level;
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

    @NonNull
    private Optional<Map<String, Object>> timeoutError(final JobClassAndId job) {
        final var latestRunMaybe = jobDataService.findLatestJobRun(job.id());
        if (latestRunMaybe.isEmpty()) {
            return Optional.empty();
        }

        final var latestRun = latestRunMaybe.get();
        if (latestRun.latestState() != JobRunState.BUSY) {
            return Optional.empty();
        }

        final Duration runtimeLimit;
        try {
            runtimeLimit =
                    jobService
                            .getProperties(job)
                            .get(JobPropertyDefinitions.RUN_TIME_LIMIT)
                            .value();
        } catch (final ClassNotFoundException e) {
            return Optional.empty();
        }

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
