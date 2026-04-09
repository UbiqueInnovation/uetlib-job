package ch.ubique.backend.job.checks;

import static java.util.Map.entry;
import static java.util.Map.ofEntries;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
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
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@Profile("!job-run")
public class JobLastRunMaxAgeCheck implements StatusCheck {

    private final CheckLevel level;
    private final JobService jobService;
    private final JobDataService jobDataService;

    public JobLastRunMaxAgeCheck(
            @Value("${ch.ubique.backend.job.check.last-run-max-age:ERROR}") final CheckLevel level,
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
                jobService.getAllJobClassesWithId().stream()
                        .map(this::checkJobOverdue)
                        .flatMap(Optional::stream)
                        .toList();

        if (!errors.isEmpty()) {
            return new CheckFailed("Some jobs are overdue.", errors);
        }

        return CheckStatus.CHECK_PASSED;
    }

    @NonNull
    private Optional<Map<String, Object>> checkJobOverdue(final JobClassAndId job) {
        final Optional<Duration> lastRunMaxAgeMaybe;
        final boolean lastRunMaxAgeAllowNoRun;
        try {
            if (!jobService.isEnabled(job.canonicalClassName())) {
                return Optional.empty();
            }
            lastRunMaxAgeMaybe =
                    jobService
                            .getProperties(job)
                            .get(JobPropertyDefinitions.LAST_RUN_MAX_AGE)
                            .valueMaybe();
            lastRunMaxAgeAllowNoRun =
                    jobService
                            .getProperties(job)
                            .get(JobPropertyDefinitions.LAST_RUN_MAX_AGE_ALLOW_NO_RUN)
                            .value();
        } catch (final ClassNotFoundException e) {
            return Optional.empty();
        }

        if (lastRunMaxAgeMaybe.isEmpty()) {
            return Optional.empty();
        }

        final var lastRunMaxAge = lastRunMaxAgeMaybe.get();

        final var lastRunMaybe = jobDataService.findLatestJobRun(job.id());

        if (lastRunMaybe.isEmpty()) {
            if (lastRunMaxAgeAllowNoRun) {
                return Optional.empty();
            }
            //noinspection ConstantValue
            return Optional.of(
                    ofEntries(
                            entry("job", job.canonicalClassName()),
                            entry("lastRunMaxAge", lastRunMaxAge),
                            entry("lastRunMaxAgeAllowNoRun", lastRunMaxAgeAllowNoRun),
                            entry("lastRunStartedAt", "null"),
                            entry("lastRunAge", "null")));
        }

        final var lastRunStartedAt = lastRunMaybe.get().startedAt();

        final var lastRunAge = Duration.between(lastRunStartedAt, Instant.now());

        if (lastRunAge.compareTo(lastRunMaxAge) <= 0) {
            return Optional.empty();
        }

        return Optional.of(
                ofEntries(
                        entry("job", job.canonicalClassName()),
                        entry("lastRunMaxAge", lastRunMaxAge),
                        entry("lastRunMaxAgeAllowNoRun", lastRunMaxAgeAllowNoRun),
                        entry("lastRunStartedAt", lastRunStartedAt),
                        entry("lastRunAge", lastRunAge)));
    }
}
