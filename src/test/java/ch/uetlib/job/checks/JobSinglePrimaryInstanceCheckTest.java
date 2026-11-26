package ch.uetlib.job.checks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobDaemon;
import ch.uetlib.monitoring.check.models.CheckFailed;
import ch.uetlib.monitoring.check.models.CheckLevel;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

class JobSinglePrimaryInstanceCheckTest {

    private static final Duration STALE_AFTER = Duration.ofSeconds(90);

    private final JobDataService jobDataService = mock(JobDataService.class);

    @Test
    void passesWhenALiveInstanceHoldsTheLease() {
        stubLiveInstances(holder("pod-a"), candidate("pod-b"), worker("pod-c"));

        assertTrue(check().check().passed());
    }

    @Test
    void failsWhenNoInstanceHasTheProfile() {
        // Nothing fires, and only a non-primary instance is left to notice - which is why this
        // runs on every instance and reads the shared table.
        stubLiveInstances(worker("pod-a"));

        final var status = check().check();

        assertFalse(status.passed());
        assertTrue(
                assertInstanceOf(CheckFailed.class, status)
                        .reason()
                        .contains("No primary candidate found"));
    }

    @Test
    void failsWhenCandidatesAreRunningButNoneHoldsTheLeaseAndNamesThem() {
        stubLiveInstances(candidate("pod-b"), worker("pod-a"));

        final var failed = assertInstanceOf(CheckFailed.class, check().check());

        assertTrue(failed.reason().contains("No instance holds the primary lease"));
        final var context = assertInstanceOf(Map.class, failed.context());
        assertEquals(List.of("pod-b (1.0.0)"), context.get("candidates"));
    }

    @Test
    void passesDuringTheStartupGraceEvenWithNoPrimaryYet() {
        // On a cold start a non-primary instance can be up before the primary has taken the lease.
        stubLiveInstances();

        assertTrue(checkWithStartupGrace(Duration.ofMinutes(10)).check().passed());
        verify(jobDataService, never()).findLiveDaemons(any());
    }

    private void stubLiveInstances(final JobDaemon... instances) {
        when(jobDataService.findLiveDaemons(any())).thenReturn(List.of(instances));
    }

    private static JobDaemon holder(final String name) {
        return instance(name, true, true);
    }

    private static JobDaemon candidate(final String name) {
        return instance(name, true, false);
    }

    private static JobDaemon worker(final String name) {
        return instance(name, false, false);
    }

    private static JobDaemon instance(
            final String name, final boolean candidate, final boolean holdsLease) {
        final var now = Instant.now();
        return new JobDaemon(
                name.hashCode(),
                name,
                "1.0.0",
                Set.of(),
                candidate,
                candidate ? 3 : null,
                holdsLease,
                holdsLease ? now : null,
                now.minus(Duration.ofHours(1)),
                now);
    }

    private JobSinglePrimaryInstanceCheck check() {
        return checkWithStartupGrace(Duration.ZERO);
    }

    private JobSinglePrimaryInstanceCheck checkWithStartupGrace(final Duration startupGrace) {
        return new JobSinglePrimaryInstanceCheck(
                CheckLevel.ERROR, jobDataService, STALE_AFTER, startupGrace);
    }
}
