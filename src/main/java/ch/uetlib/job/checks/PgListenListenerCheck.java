package ch.uetlib.job.checks;

import ch.uetlib.job.service.triggers.postgres.PgListenTriggerService;
import ch.uetlib.monitoring.check.StatusCheck;
import ch.uetlib.monitoring.check.models.CheckFailed;
import ch.uetlib.monitoring.check.models.CheckLevel;
import ch.uetlib.monitoring.check.models.CheckStatus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Verifies that every registered PostgreSQL {@code LISTEN} channel is still being actively
 * monitored. A listener can silently stop working (e.g. a half-open connection that is never
 * detected, or a dead listener thread) while the application keeps running, which causes {@code
 * PG_LISTEN}-triggered jobs to stop firing without any restart or error.
 */
@Service
@Profile("!job-run")
public class PgListenListenerCheck implements StatusCheck {

    private final CheckLevel level;
    private final PgListenTriggerService pgListenTriggerService;

    public PgListenListenerCheck(
            @Value("${ch.ubique.backend.job.check.pg-listen:ERROR}") final CheckLevel level,
            final PgListenTriggerService pgListenTriggerService) {
        this.level = level;
        this.pgListenTriggerService = pgListenTriggerService;
    }

    @Override
    public CheckLevel level() {
        return level;
    }

    @Override
    public CheckStatus check() {
        final var unhealthyChannels = pgListenTriggerService.unhealthyChannels();
        if (!unhealthyChannels.isEmpty()) {
            return new CheckFailed(
                    "Some PostgreSQL LISTEN channels are no longer being monitored.",
                    Map.of("channels", unhealthyChannels));
        }
        return CheckStatus.CHECK_PASSED;
    }
}
