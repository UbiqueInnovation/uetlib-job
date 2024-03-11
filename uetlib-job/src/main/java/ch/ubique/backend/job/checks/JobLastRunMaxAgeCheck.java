package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import com.pivovarit.function.exception.WrappedException;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@Profile("!job-run")
public class JobLastRunMaxAgeCheck implements StatusCheck {

    private final JobService jobService;
    private final JobDataService jobDataService;

    public JobLastRunMaxAgeCheck(final JobService jobService, final JobDataService jobDataService) {
        this.jobService = jobService;
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
    private Optional<Map<String, Object>> checkJobOverdue(final JobClassAndId job) {
        final Optional<Duration> lastRunMaxAgeMaybe;
        try {
            lastRunMaxAgeMaybe =
                    jobService
                            .getProperties(job)
                            .get(JobPropertyDefinitions.LAST_RUN_MAX_AGE)
                            .valueMaybe();
        } catch (ClassNotFoundException e) {
            throw new WrappedException(e);
        }

        if (lastRunMaxAgeMaybe.isEmpty()) {
            return Optional.empty();
        }

        final var lastRunMaxAge = lastRunMaxAgeMaybe.get();

        final var lastRunStartedAt =
                jobDataService
                        .findLatestJobRun(job.id())
                        .map(JobRun::startedAt)
                        .orElse(Instant.EPOCH);

        final var lastRunAge = Duration.between(lastRunStartedAt, Instant.now());

        if (lastRunAge.compareTo(lastRunMaxAge) <= 0) {
            return Optional.empty();
        }

        return Optional.of(
                Map.ofEntries(
                        Map.entry("job", job.canonicalClassName()),
                        Map.entry("lastRunMaxAge", lastRunMaxAge),
                        Map.entry("lastRunStartedAt", lastRunStartedAt),
                        Map.entry("lastRunAge", lastRunAge)));
    }
}
