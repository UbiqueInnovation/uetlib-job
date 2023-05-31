package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import jakarta.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class JobLastRunMaxAgeCheck implements StatusCheck {

    private final JobDataService jobDataService;

    public JobLastRunMaxAgeCheck(final JobDataService jobDataService) {
        this.jobDataService = jobDataService;
    }

    @Override
    public CheckStatus check() {
        final var errors =
                jobDataService.findJobs().stream()
                        .map(this::checkJobOverdue)
                        .flatMap(Optional::stream)
                        .toList();

        if (!errors.isEmpty()) {
            return new CheckFailed("Some jobs are overdue.", errors);
        }

        return CheckStatus.CHECK_PASSED;
    }

    @NotNull
    private Optional<Map<String, Object>> checkJobOverdue(final Job job) {
        final var lastRunMaxAgeMaybe =
                jobDataService
                        .getProperty(job.id(), JobPropertyDefinitions.LAST_RUN_MAX_AGE)
                        .valueMaybe();

        if (lastRunMaxAgeMaybe.isEmpty()) {
            return Optional.empty();
        }

        final var lastRunMaxAge = lastRunMaxAgeMaybe.get();

        final var lastRunStartedAt =
                jobDataService
                        .findLatestJobRun(job.id())
                        .map(JobRun::getStartedAt)
                        .orElse(Instant.EPOCH);

        final var lastRunAge = Duration.between(lastRunStartedAt, Instant.now());

        if (lastRunAge.compareTo(lastRunMaxAge) <= 0) {
            return Optional.empty();
        }

        return Optional.of(
                Map.ofEntries(
                        Map.entry("job", job.clazz()),
                        Map.entry("lastRunMaxAge", lastRunMaxAge),
                        Map.entry("lastRunStartedAt", lastRunStartedAt),
                        Map.entry("lastRunAge", lastRunAge)));
    }
}
