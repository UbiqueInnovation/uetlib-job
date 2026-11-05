package ch.uetlib.job.service.instance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobDaemon;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.triggers.TriggerCoordinatorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * The check-in loop around the primary lease: when an instance takes it, keeps it, purges, and
 * hands it over. The lease queries themselves are covered by {@code JobDaemonDatabaseTest}.
 */
class JobDaemonRegistrationServiceTest {

    private static final String NAME = "pod-a";
    private static final Instant STARTED = Instant.parse("2026-09-14T08:00:00Z");
    private static final Duration STALE_AFTER = Duration.ofSeconds(90);

    private final JobDataService jobDataService = mock(JobDataService.class);
    private final JobService jobService = mock(JobService.class);
    private final TriggerCoordinatorService coordinator = mock(TriggerCoordinatorService.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<TriggerCoordinatorService> coordinatorProvider =
            mock(ObjectProvider.class);

    private JobDaemonRegistrationService service;

    @BeforeEach
    void setUp() {
        when(jobService.versionTag()).thenReturn("1.0.0");
        when(coordinatorProvider.getIfAvailable()).thenReturn(coordinator);
        when(coordinator.isPrimaryCandidate()).thenReturn(true);
        when(coordinator.isStarted()).thenReturn(true);
        when(coordinator.registeredTriggerCount()).thenReturn(3);
        service =
                new JobDaemonRegistrationService(
                        jobDataService,
                        jobService,
                        new JobInstanceIdentity(NAME, Set.of("gpu"), STARTED),
                        coordinatorProvider,
                        STALE_AFTER,
                        null);
    }

    @Test
    void aCheckInReportsThisInstancesVersionTaintsAndTriggers() {
        checksInAs(self(false));

        service.checkIn();

        verify(jobDataService).checkInDaemon(NAME, "1.0.0", Set.of("gpu"), true, 3, STARTED);
    }

    @Test
    void theNewestLiveCandidateTakesTheLeaseAndPurgesInstancesThatStoppedCheckingIn() {
        checksInAs(self(false));
        live(self(false), candidate("pod-old", STARTED.minusSeconds(600)));
        when(jobDataService.takePrimaryLease(eq(NAME), eq(STARTED), any())).thenReturn(true);

        service.checkIn();

        assertTrue(service.isPrimary());
        final var purgedBefore = ArgumentCaptor.forClass(Instant.class);
        verify(jobDataService).deleteStaleInstances(purgedBefore.capture());
        // purge-after defaults to three times stale-after
        final var purgeAfter = Duration.between(purgedBefore.getValue(), Instant.now());
        assertTrue(
                purgeAfter.minus(STALE_AFTER.multipliedBy(3)).abs().compareTo(Duration.ofSeconds(5))
                        < 0,
                "purged after " + purgeAfter);
    }

    @Test
    void aCandidateWhoseTriggersAreNotRegisteredYetDoesNotTakeTheLease() {
        when(coordinator.isStarted()).thenReturn(false);
        checksInAs(self(false));
        live(self(false));

        service.checkIn();

        verify(jobDataService, never()).takePrimaryLease(any(), any(), any());
        assertFalse(service.isPrimary());
    }

    @Test
    void aCandidateOutrankedByANewerLiveCandidateLeavesTheLeaseToIt() {
        checksInAs(self(false));
        live(self(false), candidate("pod-new", STARTED.plusSeconds(60)));

        service.checkIn();

        verify(jobDataService, never()).takePrimaryLease(any(), any(), any());
        assertFalse(service.isPrimary());
    }

    @Test
    void aNewerInstanceWithoutThePrimaryTriggersProfileDoesNotOutrankACandidate() {
        checksInAs(self(false));
        live(self(false), worker("worker-new", STARTED.plusSeconds(60)));
        when(jobDataService.takePrimaryLease(eq(NAME), eq(STARTED), any())).thenReturn(true);

        service.checkIn();

        assertTrue(service.isPrimary());
    }

    @Test
    void anInstanceWithoutThePrimaryTriggersProfileChecksInButNeverCompetes() {
        when(coordinatorProvider.getIfAvailable()).thenReturn(null);
        checksInAs(worker(NAME, STARTED));

        service.checkIn();

        verify(jobDataService).checkInDaemon(NAME, "1.0.0", Set.of("gpu"), false, null, STARTED);
        verify(jobDataService, never()).takePrimaryLease(any(), any(), any());
        verify(jobDataService, never()).deleteStaleInstances(any());
        assertFalse(service.isPrimary());
    }

    @Test
    void theHolderKeepsTheLeaseWithoutTakingItAgain() {
        checksInAs(self(true));

        service.checkIn();

        assertTrue(service.isPrimary());
        verify(jobDataService, never()).takePrimaryLease(any(), any(), any());
        verify(jobDataService).deleteStaleInstances(any());
    }

    @Test
    void aHolderThatLostTheLeaseStopsReportingPrimaryAndStopsPurging() {
        checksInAs(self(true));
        service.checkIn();
        checksInAs(self(false));
        live(self(false), candidate("pod-new", STARTED.plusSeconds(60)));
        clearInvocations(jobDataService);

        service.checkIn();

        assertFalse(service.isPrimary());
        verify(jobDataService, never()).deleteStaleInstances(any());
    }

    @Test
    void losingARaceForTheLeaseLeavesTheInstanceANonPrimaryWithoutFailingTheCheckIn() {
        checksInAs(self(false));
        live(self(false));
        when(jobDataService.takePrimaryLease(eq(NAME), eq(STARTED), any()))
                .thenThrow(new DataIntegrityViolationException("primary_slot"));

        assertDoesNotThrow(service::checkIn);

        assertFalse(service.isPrimary());
        verify(jobDataService, never()).deleteStaleInstances(any());
    }

    @Test
    void aFailingCheckInIsLeftToTheNextOneInsteadOfPropagating() {
        when(jobDataService.checkInDaemon(any(), any(), any(), anyBoolean(), any(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertDoesNotThrow(service::checkIn);

        assertFalse(service.isPrimary());
    }

    @Test
    void aPrimaryShuttingDownHandsTheLeaseToTheNewestOtherLiveCandidate() {
        checksInAs(self(true));
        service.checkIn();
        live(
                self(true),
                candidate("pod-old", STARTED.minusSeconds(600)),
                candidate("pod-new", STARTED.plusSeconds(60)),
                worker("worker-newest", STARTED.plusSeconds(120)));

        service.leave();

        verify(jobDataService).removeDaemon(eq(NAME), eq("pod-new"), any());
    }

    @Test
    void aNonPrimaryShuttingDownRemovesItselfWithoutHandingAnythingOver() {
        service.leave();

        verify(jobDataService).removeDaemon(eq(NAME), isNull(), any());
        verify(jobDataService, never()).findLiveDaemons(any());
    }

    @Test
    void aFailingRemovalOnShutdownIsLeftToThePurge() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(jobDataService)
                .removeDaemon(any(), any(), any());

        assertDoesNotThrow(service::leave);
    }

    private void checksInAs(final JobDaemon self) {
        when(jobDataService.checkInDaemon(any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(self);
    }

    private void live(final JobDaemon... instances) {
        when(jobDataService.findLiveDaemons(any())).thenReturn(List.of(instances));
    }

    private static JobDaemon self(final boolean holdsLease) {
        return instance(NAME, true, holdsLease, STARTED);
    }

    private static JobDaemon candidate(final String name, final Instant startedAt) {
        return instance(name, true, false, startedAt);
    }

    /** An instance without {@code job-primary-triggers}: it runs jobs but never takes the lease. */
    private static JobDaemon worker(final String name, final Instant startedAt) {
        return instance(name, false, false, startedAt);
    }

    private static JobDaemon instance(
            final String name,
            final boolean candidate,
            final boolean holdsLease,
            final Instant startedAt) {
        return new JobDaemon(
                1,
                name,
                "1.0.0",
                Set.of(),
                candidate,
                candidate ? 3 : null,
                holdsLease,
                holdsLease ? startedAt : null,
                startedAt,
                Instant.now());
    }
}
