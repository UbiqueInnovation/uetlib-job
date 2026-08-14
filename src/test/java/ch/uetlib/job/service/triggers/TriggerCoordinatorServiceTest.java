package ch.uetlib.job.service.triggers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.Job;
import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRunContext;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.triggers.CronTrigger;
import ch.uetlib.job.model.triggers.JobTriggersConfig;
import ch.uetlib.job.model.triggers.RetryTrigger;
import ch.uetlib.job.model.triggers.Trigger;
import ch.uetlib.job.model.triggers.TriggerDefinition;
import ch.uetlib.job.service.JobService;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Covers {@link TriggerCoordinatorService#getTriggers}, in particular the fixed-triggers
 * reconciliation path - it must diff definitions rather than delete-all/reinsert-all, so an
 * unrelated, unchanged trigger on the same job keeps its id when a sibling trigger is added,
 * removed, or changed (see SCHEDULING.md).
 */
class TriggerCoordinatorServiceTest {

    private static final int JOB_ID = 1;
    private static final Class<? extends Job> JOB_CLASS = TestJob.class;
    private static final JobClassAndId JOB_CLASS_AND_ID = new JobClassAndId(JOB_ID, JOB_CLASS);

    private static final CronTrigger CRON = new CronTrigger("0 0 2 * * ?", null);
    private static final RetryTrigger RETRY_V1 = new RetryTrigger(1, Duration.ofMinutes(5));
    private static final RetryTrigger RETRY_V2 = new RetryTrigger(3, Duration.ofMinutes(5));

    private final JobDataService jobDataService = mock(JobDataService.class);
    private final JobService jobService = mock(JobService.class);

    private final TriggerCoordinatorService coordinator =
            new TriggerCoordinatorService(jobDataService, jobService, List.of(), environment());

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
        final var environment = new MockEnvironment();
        environment.setActiveProfiles(TriggerCoordinatorService.PRIMARY_TRIGGERS_PROFILE);
        return environment;
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
