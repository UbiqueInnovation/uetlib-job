package ch.uetlib.job.service.triggers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.Job;
import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobRunContext;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.triggers.CronTrigger;
import ch.uetlib.job.model.triggers.JobTriggersConfig;
import ch.uetlib.job.model.triggers.RetryTrigger;
import ch.uetlib.job.model.triggers.Trigger;
import ch.uetlib.job.model.triggers.TriggerDefinition;
import ch.uetlib.job.model.triggers.TriggerType;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.service.instance.JobInstanceIdentity;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Mainly {@link TriggerCoordinatorService#getTriggers}: fixed triggers are diffed, so unchanged
 * ones keep their ids.
 */
class TriggerCoordinatorServiceTest {

    private static final int JOB_ID = 1;
    private static final Class<? extends Job> JOB_CLASS = TestJob.class;
    private static final JobClassAndId JOB_CLASS_AND_ID = new JobClassAndId(JOB_ID, JOB_CLASS);
    private static final String INSTANCE_NAME = "pod-a";
    private static final JobInstanceIdentity IDENTITY =
            new JobInstanceIdentity(INSTANCE_NAME, Set.of(), Instant.now());

    private static final CronTrigger CRON = new CronTrigger("0 0 2 * * ?", null);
    private static final RetryTrigger RETRY_V1 = new RetryTrigger(1, Duration.ofMinutes(5));
    private static final RetryTrigger RETRY_V2 = new RetryTrigger(3, Duration.ofMinutes(5));

    private final JobDataService jobDataService = mock(JobDataService.class);
    private final JobService jobService = mock(JobService.class);

    private final TriggerCoordinatorService coordinator =
            new TriggerCoordinatorService(
                    jobDataService, jobService, IDENTITY, List.of(), environment());

    @Test
    @SuppressWarnings("unchecked")
    void aFiringTriggerQueuesThroughThePrimaryLeaseCheckUnderThisInstancesName() {
        // Whether the run is queued is decided in the database, in the same transaction as the
        // insert - never by a flag on this instance, which could be stale.
        final TriggerService<CronTrigger> cronService = mock(TriggerService.class);
        when(cronService.triggerType()).thenReturn(TriggerType.CRON);
        final var firingCoordinator =
                new TriggerCoordinatorService(
                        jobDataService, jobService, IDENTITY, List.of(cronService), environment());
        when(jobService.jobClassAndIdOrThrow(JOB_CLASS)).thenReturn(JOB_CLASS_AND_ID);

        firingCoordinator.onStartup(new Trigger(1, JOB_ID, CRON), JOB_CLASS_AND_ID, JOB_CLASS);

        final ArgumentCaptor<Consumer<JobRunArguments>> fireCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(cronService).onStartup(eq(1), eq(CRON), eq(JOB_CLASS_AND_ID), fireCaptor.capture());
        fireCaptor.getValue().accept(new JobRunArguments());

        verify(jobService).queueNewRunAsPrimary(eq(INSTANCE_NAME), eq(JOB_CLASS_AND_ID), any());
        verify(jobService, never()).queueNewRun(any(), any());
    }

    @Test
    void anInstanceWithTriggersDisabledDoesNotCompeteForTheLease() {
        final var environment = environment();
        environment.addActiveProfile("job-disable-triggers");
        final var disabled =
                new TriggerCoordinatorService(
                        jobDataService, jobService, IDENTITY, List.of(), environment);

        assertFalse(disabled.isPrimaryCandidate());
        assertTrue(coordinator.isPrimaryCandidate());
        assertFalse(coordinator.isStarted(), "triggers are only registered on ApplicationReady");
    }

    @Test
    @SuppressWarnings("unchecked")
    void preparingReconcilesAndMarksStartedWithoutSchedulingAnyTrigger() {
        // A FixedRateTrigger fires as soon as it is scheduled; without the lease by then, that
        // first firing is dropped and the job waits a full period.
        final TriggerService<CronTrigger> cronService = mock(TriggerService.class);
        when(cronService.triggerType()).thenReturn(TriggerType.CRON);
        final var startingCoordinator =
                new TriggerCoordinatorService(
                        jobDataService, jobService, IDENTITY, List.of(cronService), environment());
        stubJobClass();
        doReturn(List.of(JOB_CLASS)).when(jobService).getAllJobClasses();
        doReturn(fixedJob(CRON)).when(jobService).instantiateJob(JOB_CLASS);
        when(jobDataService.getAllDbTriggersByJobId())
                .thenReturn(Map.of(JOB_ID, List.of(new Trigger(1, JOB_ID, CRON))));
        startingCoordinator.prepareTriggers();

        // What JobDaemonRegistrationService#checkIn needs to take the lease and report.
        assertTrue(startingCoordinator.isStarted());
        assertEquals(1, startingCoordinator.registeredTriggerCount());
        verify(cronService, never()).onStartup(anyInt(), any(), any(), any());

        startingCoordinator.scheduleTriggers();

        verify(cronService).onStartup(eq(1), eq(CRON), eq(JOB_CLASS_AND_ID), any());
        assertEquals(1, startingCoordinator.registeredTriggerCount());
        // The phases run once each, in order.
        assertThrows(IllegalStateException.class, startingCoordinator::scheduleTriggers);
        assertThrows(IllegalStateException.class, startingCoordinator::prepareTriggers);
    }

    @Test
    void schedulingWithoutPreparingFails() {
        assertThrows(IllegalStateException.class, coordinator::scheduleTriggers);
        assertFalse(coordinator.isStarted());
    }

    @Test
    void anInstanceWithTriggersDisabledPreparesAndSchedulesNothing() {
        final var environment = environment();
        environment.addActiveProfile("job-disable-triggers");
        final var disabled =
                new TriggerCoordinatorService(
                        jobDataService, jobService, IDENTITY, List.of(), environment);
        disabled.prepareTriggers();
        disabled.scheduleTriggers();

        assertFalse(disabled.isStarted());
        verifyNoInteractions(jobDataService);
    }

    @Test
    void definitionsUnchangedReturnsDbTriggersWithoutAnyDatabaseWrites() {
        stubJobClass();
        final var dbTriggers =
                List.of(new Trigger(1, JOB_ID, CRON), new Trigger(2, JOB_ID, RETRY_V1));

        final var result =
                coordinator.getTriggers(fixedJob(CRON, RETRY_V1), Map.of(JOB_ID, dbTriggers));

        assertEquals(dbTriggers, result);
        verifyNoInteractions(jobDataService);
    }

    @Test
    void addingATriggerKeepsExistingIdsAndOnlyInsertsTheNewOne() {
        stubJobClass();
        final var existingCron = new Trigger(1, JOB_ID, CRON);
        final var dbTriggers = List.of(existingCron);
        final var insertedRetry = new Trigger(2, JOB_ID, RETRY_V1);
        when(jobDataService.insertTriggers(JOB_ID, List.of(RETRY_V1)))
                .thenReturn(List.of(insertedRetry));

        final var result =
                coordinator.getTriggers(fixedJob(CRON, RETRY_V1), Map.of(JOB_ID, dbTriggers));

        assertEquals(Set.copyOf(List.of(existingCron, insertedRetry)), Set.copyOf(result));
        verify(jobDataService, never()).deleteTrigger(any());
        verify(jobDataService, never()).deleteTriggerForJob(anyInt());
    }

    @Test
    void removingATriggerDeletesOnlyThatOneAndInsertsNothing() {
        stubJobClass();
        final var keptCron = new Trigger(1, JOB_ID, CRON);
        final var obsoleteRetry = new Trigger(2, JOB_ID, RETRY_V1);
        final var dbTriggers = List.of(keptCron, obsoleteRetry);

        final var result = coordinator.getTriggers(fixedJob(CRON), Map.of(JOB_ID, dbTriggers));

        assertEquals(List.of(keptCron), result);
        verify(jobDataService).deleteTrigger(obsoleteRetry);
        verify(jobDataService, never()).deleteTrigger(keptCron);
        verify(jobDataService, never()).insertTriggers(anyInt(), any());
    }

    @Test
    void changingATriggerKeepsUnrelatedTriggersAndReplacesOnlyTheChangedOne() {
        stubJobClass();
        final var keptCron = new Trigger(1, JOB_ID, CRON);
        final var oldRetry = new Trigger(2, JOB_ID, RETRY_V1);
        final var dbTriggers = List.of(keptCron, oldRetry);
        final var newRetry = new Trigger(3, JOB_ID, RETRY_V2);
        when(jobDataService.insertTriggers(JOB_ID, List.of(RETRY_V2)))
                .thenReturn(List.of(newRetry));

        final var result =
                coordinator.getTriggers(fixedJob(CRON, RETRY_V2), Map.of(JOB_ID, dbTriggers));

        assertEquals(Set.copyOf(List.of(keptCron, newRetry)), Set.copyOf(result));
        verify(jobDataService).deleteTrigger(oldRetry);
        verify(jobDataService, never()).deleteTrigger(keptCron);
        verify(jobDataService, never()).deleteTriggerForJob(anyInt());
    }

    private void stubJobClass() {
        when(jobService.jobClassAndIdOrThrow(JOB_CLASS)).thenReturn(JOB_CLASS_AND_ID);
    }

    private static Job fixedJob(final TriggerDefinition... triggerDefinitions) {
        return new TestJob(JobTriggersConfig.fixed(triggerDefinitions));
    }

    private static MockEnvironment environment() {
        return new MockEnvironment();
    }

    private record TestJob(JobTriggersConfig triggersConfig) implements Job {
        @Override
        public JobRunState run(final JobRunContext jobRunContext) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JobTriggersConfig triggersConfig() {
            return triggersConfig;
        }
    }
}
