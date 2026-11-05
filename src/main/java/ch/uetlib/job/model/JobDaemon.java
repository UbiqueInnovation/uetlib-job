package ch.uetlib.job.model;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Set;

/** A long-lived instance, as it last checked in to {@code t_job_daemon}. */
public record JobDaemon(
        int jobDaemonId,
        String daemonName,
        String version,
        Set<String> taints,
        boolean primaryCandidate,
        @Nullable Integer triggerCount,
        boolean holdsPrimaryLease,
        @Nullable Instant primarySince,
        Instant startedAt,
        Instant updatedAt) {

    /** Whether {@code other} started later and so outranks this one; the name breaks ties. */
    public boolean isSupersededBy(final JobDaemon other) {
        final var byStart = other.startedAt().compareTo(startedAt);
        if (byStart != 0) {
            return byStart > 0;
        }
        return other.daemonName().compareTo(daemonName) > 0;
    }
}
