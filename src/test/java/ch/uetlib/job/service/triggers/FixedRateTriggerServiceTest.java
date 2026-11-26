package ch.uetlib.job.service.triggers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.triggers.FixedRateTrigger;
import ch.uetlib.job.model.triggers.TriggerType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

/**
 * Fixed-rate triggers are scheduled only on the primary lease holder and keep the job's rhythm
 * across a lease move, so a rollout neither drops nor duplicates a run.
 */
class FixedRateTriggerServiceTest {

    private static final int TRIGGER_ID = 7;
    private static final JobClassAndId JOB = new JobClassAndId(3, "ch.example.HealthCheckJob");
    private static final Duration PERIOD = Duration.ofMinutes(10);
    private static final Duration TOLERANCE = Duration.ofSeconds(5);

    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final JobDataService jobDataService = mock(JobDataService.class);
    private final ScheduledFuture<?> future = mock(ScheduledFuture.class);
    private final List<JobRunArguments> fired = new ArrayList<>();

    private final FixedRateTriggerService service =
            new FixedRateTriggerService(taskScheduler, jobDataService);

    @BeforeEach
    void setUp() {
        doReturn(future).when(taskScheduler).scheduleAtFixedRate(any(), any(Instant.class), any());
    }

    @Test
    void aNewPrimaryQueuesAnOverdueFixedRateJobPromptly() {
        // The 2026-11-23 rollout: last run 25 minutes ago, longer than the period.
        lastQueued(Instant.now().minus(Duration.ofMinutes(25)));
        register(new FixedRateTrigger(PERIOD));

        service.onPrimaryLease(true);

        final var start = scheduledStart();
        assertClose(Instant.now(), start);
        firePeriodically().run();
        assertEquals(TriggerType.FIXED_RATE, fired.getFirst().triggerType());
    }

    @Test
    void aJobThatRanRecentlyIsNotRunAgainEarly() {
        final var lastQueuedAt = Instant.now().minus(Duration.ofMinutes(3));
        lastQueued(lastQueuedAt);
        register(new FixedRateTrigger(PERIOD));

        service.onPrimaryLease(true);

        // One period after the previous primary's run, not right away and not a period from now.
        assertEquals(lastQueuedAt.plus(PERIOD), scheduledStart());
    }

    @Test
    void triggersRegisteredWhileNotPrimaryAreNotScheduled() {
        lastQueued(Instant.now().minus(Duration.ofHours(1)));
        register(new FixedRateTrigger(PERIOD));

        service.onPrimaryLease(false);

        verify(taskScheduler, never()).scheduleAtFixedRate(any(), any(Instant.class), any());
        verifyNoInteractions(jobDataService);
        assertFalse(service.isScheduled(TRIGGER_ID));
        assertTrue(fired.isEmpty());
    }

    @Test
    void losingTheLeaseCancelsTheTriggerAndRetakingItSchedulesItFromTheLatestRun() {
        register(new FixedRateTrigger(PERIOD));
        lastQueued(Instant.now().minus(Duration.ofHours(1)));
        service.onPrimaryLease(true);

        service.onPrimaryLease(false);

        verify(future).cancel(false);
        assertFalse(service.isScheduled(TRIGGER_ID));

        // Another instance ran the job in the meantime.
        final var otherInstancesRun = Instant.now().minus(Duration.ofMinutes(2));
        lastQueued(otherInstancesRun);
        clearInvocations(taskScheduler);
        service.onPrimaryLease(true);

        assertEquals(otherInstancesRun.plus(PERIOD), scheduledStart());
    }

    @Test
    void stayingPrimarySchedulesEachTriggerOnlyOnce() {
        register(new FixedRateTrigger(PERIOD));

        service.onPrimaryLease(true);
        service.onPrimaryLease(true);
        service.onPrimaryLease(true);

        verify(taskScheduler, times(1)).scheduleAtFixedRate(any(), any(Instant.class), any());
        verify(jobDataService, times(1)).findLastQueuedAt(JOB.id(), TriggerType.FIXED_RATE);
    }

    @Test
    void aJobNeverQueuedByAFixedRateTriggerStartsAfterItsInitialDelay() {
        register(new FixedRateTrigger(PERIOD, Duration.ofMinutes(2)));

        service.onPrimaryLease(true);

        assertClose(Instant.now().plus(Duration.ofMinutes(2)), scheduledStart());
    }

    @Test
    void anOverdueJobStillWaitsForItsInitialDelayAfterStartup() {
        final var now = Instant.parse("2026-11-23T14:14:42Z");
        final var earliestStart = now.plus(Duration.ofMinutes(1));
        final var overdue = now.minus(Duration.ofMinutes(25));

        assertEquals(
                earliestStart,
                FixedRateTriggerService.firstFiring(now, earliestStart, overdue, PERIOD));
    }

    @Test
    void aTriggerAddedWhilePrimaryIsScheduledRightAway() {
        service.onPrimaryLease(true);

        register(new FixedRateTrigger(PERIOD));

        assertTrue(service.isScheduled(TRIGGER_ID));
        assertClose(Instant.now(), scheduledStart());
    }

    @Test
    void aFailedLookupIsRetriedAtTheNextCheckIn() {
        register(new FixedRateTrigger(PERIOD));
        when(jobDataService.findLastQueuedAt(anyInt(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertDoesNotThrow(() -> service.onPrimaryLease(true));
        assertFalse(service.isScheduled(TRIGGER_ID));

        lastQueued(Instant.now().minus(Duration.ofMinutes(1)));
        service.onPrimaryLease(true);

        assertTrue(service.isScheduled(TRIGGER_ID));
    }

    @Test
    void removingATriggerCancelsIt() {
        register(new FixedRateTrigger(PERIOD));
        service.onPrimaryLease(true);

        service.removeTrigger(TRIGGER_ID);

        verify(future).cancel(false);
        assertFalse(service.isScheduled(TRIGGER_ID));
        clearInvocations(taskScheduler);
        service.onPrimaryLease(true);
        verify(taskScheduler, never()).scheduleAtFixedRate(any(), any(Instant.class), any());
    }

    @Test
    void triggerType() {
        assertEquals(TriggerType.FIXED_RATE, service.triggerType());
    }

    private void register(final FixedRateTrigger trigger) {
        service.onStartup(TRIGGER_ID, trigger, JOB, fired::add);
    }

    private void lastQueued(final Instant lastQueuedAt) {
        doReturn(Optional.of(lastQueuedAt))
                .when(jobDataService)
                .findLastQueuedAt(JOB.id(), TriggerType.FIXED_RATE);
    }

    private Instant scheduledStart() {
        final var start = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).scheduleAtFixedRate(any(), start.capture(), eq(PERIOD));
        return start.getValue();
    }

    private Runnable firePeriodically() {
        final var runnable = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).scheduleAtFixedRate(runnable.capture(), any(Instant.class), any());
        return runnable.getValue();
    }

    private static void assertClose(final Instant expected, final Instant actual) {
        assertTrue(
                Duration.between(expected, actual).abs().compareTo(TOLERANCE) < 0,
                "expected about " + expected + " but was " + actual);
    }
}
