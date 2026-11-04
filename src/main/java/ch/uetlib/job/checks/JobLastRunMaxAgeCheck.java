package ch.uetlib.job.checks;

import static ch.uetlib.core.utils.MapCollectors.toMap;

import static java.util.Map.entry;
import static java.util.Map.ofEntries;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.properties.JobPropertyDefinitions;
import ch.uetlib.job.service.JobService;
import ch.uetlib.monitoring.check.StatusCheck;
import ch.uetlib.monitoring.check.models.CheckFailed;
import ch.uetlib.monitoring.check.models.CheckLevel;
import ch.uetlib.monitoring.check.models.CheckStatus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
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
        final var jobs = jobService.getEnabledJobClassesWithId();
        final var jobIds = jobs.stream().map(JobClassAndId::id).toList();
        final var latestRunsByJob =
                jobDataService.findAllLatestRuns(jobIds).stream().collect(toMap(JobRun::jobId));
        final var errors =
                jobs.stream()
                        .map(j -> checkJobOverdue(j, latestRunsByJob))
                        .flatMap(Optional::stream)
                        .toList();

        if (!errors.isEmpty()) {
            return new CheckFailed("Some jobs are overdue.", errors);
        }

        return CheckStatus.CHECK_PASSED;
    }

    private Optional<Map<String, Object>> checkJobOverdue(
            final JobClassAndId job, final Map<Integer, JobRun> latestRunByJob) {
        final Optional<Duration> lastRunMaxAgeMaybe;
        final boolean lastRunMaxAgeAllowNoRun;
        try {
            final var properties = jobService.getProperties(job);
            lastRunMaxAgeMaybe =
                    properties.get(JobPropertyDefinitions.LAST_RUN_MAX_AGE).valueMaybe();
            lastRunMaxAgeAllowNoRun =
                    Boolean.TRUE.equals(
                            properties
                                    .get(JobPropertyDefinitions.LAST_RUN_MAX_AGE_ALLOW_NO_RUN)
                                    .value());
        } catch (final NoSuchElementException e) {
            return Optional.empty();
        }

        if (lastRunMaxAgeMaybe.isEmpty()) {
            return Optional.empty();
        }

        final var lastRunMaxAge = lastRunMaxAgeMaybe.get();

        final var lastRunMaybe = Optional.ofNullable(latestRunByJob.get(job.id()));

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
