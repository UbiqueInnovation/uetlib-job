package ch.uetlib.job.checks;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobDaemon;
import ch.uetlib.job.service.instance.JobDaemonRegistrationService;
import ch.uetlib.job.service.triggers.TriggerCoordinatorService;
import ch.uetlib.monitoring.check.StatusCheck;
import ch.uetlib.monitoring.check.models.CheckFailed;
import ch.uetlib.monitoring.check.models.CheckLevel;
import ch.uetlib.monitoring.check.models.CheckStatus;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Fails when no live instance holds the primary lease. Runs on every instance, since a missing
 * primary can't report itself.
 */
@Service
@Profile("!job-run")
public class JobSinglePrimaryInstanceCheck implements StatusCheck {

    private final CheckLevel level;
    private final JobDataService jobDataService;
    private final Duration staleAfter;
    private final Duration startupGrace;
    private final Instant startedAt = Instant.now();

    public JobSinglePrimaryInstanceCheck(
            @Value("${ch.uetlib.job.check.single-primary-instance:ERROR}")
                    final CheckLevel level,
            final JobDataService jobDataService,
            @Value(
                            "${"
                                    + JobDaemonRegistrationService.STALE_AFTER_PROP
                                    + ":"
                                    + JobDaemonRegistrationService.STALE_AFTER_DEFAULT
                                    + "}")
                    final Duration staleAfter,
            @Value("${ch.uetlib.job.check.single-primary-instance.startup-grace:PT2M}")
                    final Duration startupGrace) {
        this.level = level;
        this.jobDataService = jobDataService;
        this.staleAfter = staleAfter;
        this.startupGrace = startupGrace;
    }

    @Override
    public CheckLevel level() {
        return level;
    }

    @Override
    public CheckStatus check() {
        if (Duration.between(startedAt, Instant.now()).compareTo(startupGrace) < 0) {
            // On a cold start a non-primary instance can be up before the primary has taken the
            // lease; reporting that as "no primary" would fail every deployment for a minute.
            return CheckStatus.CHECK_PASSED;
        }

        final var live = jobDataService.findLiveDaemons(Instant.now().minus(staleAfter));
        if (live.stream().anyMatch(JobDaemon::holdsPrimaryLease)) {
            return CheckStatus.CHECK_PASSED;
        }
        final var candidates =
                live.stream()
                        .filter(JobDaemon::primaryCandidate)
                        .map(i -> i.daemonName() + " (" + i.version() + ")")
                        .sorted()
                        .toList();
        if (candidates.isEmpty()) {
            return new CheckFailed(
                    "No instance is running with the "
                            + TriggerCoordinatorService.PRIMARY_TRIGGERS_PROFILE
                            + " profile, so no trigger fires and no scheduled run is queued.",
                    Map.of("staleAfter", staleAfter.toString()));
        }
        return new CheckFailed(
                "No instance holds the primary lease, so no trigger fires. A candidate takes it at"
                        + " its next check-in once the previous holder counts as stale.",
                Map.ofEntries(
                        Pair.of("staleAfter", staleAfter.toString()),
                        Pair.of("candidates", candidates)));
    }
}
