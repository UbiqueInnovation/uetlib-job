package ch.uetlib.job.service.instance;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobDaemon;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.triggers.TriggerCoordinatorService;

import jakarta.annotation.PreDestroy;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Checks this instance in to {@code t_job_daemon}. The newest live candidate takes the primary
 * lease; the holder purges stale rows and hands the lease over on shutdown (SCHEDULING.md §1).
 */
@Service
@Profile("!job-run")
public class JobDaemonRegistrationService {

    private static final Logger logger =
            LoggerFactory.getLogger(JobDaemonRegistrationService.class);

    public static final String STALE_AFTER_PROP = "ch.uetlib.job.instance.stale-after";
    public static final String STALE_AFTER_DEFAULT = "PT90S";

    private static final int PURGE_AFTER_STALE_MULTIPLIER = 3;

    private final JobDataService jobDataService;
    private final JobInstanceIdentity identity;
    private final ObjectProvider<TriggerCoordinatorService> triggerCoordinator;
    private final String version;
    private final Duration staleAfter;
    private final Duration purgeAfter;

    private volatile boolean primary;

    public JobDaemonRegistrationService(
            final JobDataService jobDataService,
            final JobService jobService,
            final JobInstanceIdentity identity,
            final ObjectProvider<TriggerCoordinatorService> triggerCoordinator,
            @Value("${" + STALE_AFTER_PROP + ":" + STALE_AFTER_DEFAULT + "}")
                    final Duration staleAfter,
            @Value("${ch.uetlib.job.instance.purge-after:#{null}}")
                    final @Nullable Duration purgeAfter) {
        this.jobDataService = jobDataService;
        this.identity = identity;
        this.triggerCoordinator = triggerCoordinator;
        this.version = jobService.versionTag();
        this.staleAfter = staleAfter;
        this.purgeAfter =
                requireNonNull(
                        requireNonNullElse(
                                purgeAfter, staleAfter.multipliedBy(PURGE_AFTER_STALE_MULTIPLIER)));
    }

    @Scheduled(
            initialDelayString = "0",
            fixedDelayString = "${ch.uetlib.job.instance.check-in:PT30S}")
    public synchronized void checkIn() {
        try {
            final var coordinator = triggerCoordinator.getIfAvailable();
            final var candidate = coordinator != null && coordinator.isPrimaryCandidate();
            final var self =
                    jobDataService.checkInDaemon(
                            identity.name(),
                            version,
                            identity.taints(),
                            candidate,
                            coordinator != null ? coordinator.registeredTriggerCount() : null,
                            identity.startedAt());
            var holdsLease = self.holdsPrimaryLease();
            if (!holdsLease
                    && coordinator != null
                    && candidate
                    && coordinator.isStarted()
                    && isNewestLiveCandidate(self)) {
                holdsLease = takePrimaryLease(self);
            }
            setPrimary(holdsLease);
            if (holdsLease) {
                final var purged =
                        jobDataService.deleteStaleInstances(Instant.now().minus(purgeAfter));
                if (purged > 0) {
                    logger.info("Removed {} instance(s) that stopped checking in.", purged);
                }
            }
        } catch (final Exception e) {
            // Not an error by itself: the next check-in retries, and should the primary stay
            // unable to renew its lease, JobSinglePrimaryInstanceCheck reports the missing primary.
            logger.info("Could not check in this instance; trying again at the next check-in.", e);
        }
    }

    public boolean isPrimary() {
        return primary;
    }

    /** Instances that checked in within {@value #STALE_AFTER_PROP}. */
    public List<JobDaemon> liveDaemons() {
        return jobDataService.findLiveDaemons(staleBefore());
    }

    @PreDestroy
    public synchronized void leave() {
        try {
            final var handOverTo =
                    primary
                            ? newestOtherLiveCandidate().map(JobDaemon::daemonName).orElse(null)
                            : null;
            jobDataService.removeDaemon(identity.name(), handOverTo, staleBefore());
            if (handOverTo != null) {
                logger.info("Handed the primary lease to {} on shutdown.", handOverTo);
            }
        } catch (final Exception e) {
            logger.info("Could not remove this instance on shutdown; it is purged once stale.", e);
        }
    }

    private boolean takePrimaryLease(final JobDaemon self) {
        try {
            return jobDataService.takePrimaryLease(
                    self.daemonName(), self.startedAt(), staleBefore());
        } catch (final DataIntegrityViolationException _) {
            logger.info("Another instance took the primary lease at the same moment.");
            return false;
        }
    }

    private boolean isNewestLiveCandidate(final JobDaemon self) {
        return liveCandidates().noneMatch(self::isSupersededBy);
    }

    private Optional<JobDaemon> newestOtherLiveCandidate() {
        return liveCandidates()
                .filter(instance -> !instance.daemonName().equals(identity.name()))
                .reduce((a, b) -> a.isSupersededBy(b) ? b : a);
    }

    private Stream<JobDaemon> liveCandidates() {
        return liveDaemons().stream().filter(JobDaemon::primaryCandidate);
    }

    private Instant staleBefore() {
        return Instant.now().minus(staleAfter);
    }

    private void setPrimary(final boolean primary) {
        if (this.primary != primary) {
            logger.info(
                    primary
                            ? "This instance now holds the primary lease: its triggers enqueue runs."
                            : "This instance no longer holds the primary lease: its triggers stop"
                                    + " enqueueing runs.");
        }
        this.primary = primary;
    }
}
