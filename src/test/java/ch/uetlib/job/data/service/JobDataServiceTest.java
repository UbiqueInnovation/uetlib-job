package ch.uetlib.job.data.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.core.exception.ResourceNotFoundException;
import ch.uetlib.job.data.repository.*;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.entity.JobRunEntity;
import ch.uetlib.job.model.entity.JobRunStateTransitionEntity;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

/**
 * Pure-mock unit coverage for {@link JobDataService#updateJobRunState}, whose branch combinations
 * (running-with-stale-finishedAt, ACKNOWLEDGED-while-acknowledgeable, already-finished-no-op,
 * finished/running finishedAt bookkeeping) aren't exercised by {@link JobDataServiceDatabaseTest}.
 * Complements it rather than replacing it.
 */
class JobDataServiceTest {

    private final JobRunRepository jobRunRepository = mock(JobRunRepository.class);
    private final JobRunStateTransitionRepository jobRunStateTransitionRepository =
            mock(JobRunStateTransitionRepository.class);
    private final JobDataService jobDataService =
            new JobDataService(
                    mock(JobRepository.class),
                    mock(JobTriggerRepository.class),
                    mock(JobPropertyRepository.class),
                    jobRunRepository,
                    jobRunStateTransitionRepository,
                    mock(JobRunInputRepository.class),
                    mock(JobRunDatabaseSubmissionRepository.class),
                    mock(JobQueuedRunRepository.class),
                    mock(K8sJobRepository.class),
                    mock(LoggingEventRepository.class),
                    mock(JdbcTemplate.class));

    private static final int JOB_RUN_ID = 42;

    @BeforeEach
    void stubSaveToEchoInput() {
        when(jobRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void throwsResourceNotFoundWhenTheJobRunDoesNotExist() {
        when(jobRunRepository.findById(JOB_RUN_ID)).thenReturn(Optional.empty());

        final var now = Instant.now();
        assertThrows(
                ResourceNotFoundException.class,
                () -> jobDataService.updateJobRunState(JOB_RUN_ID, JobRunState.DONE, now));
    }

    @Test
    void runningStateWithAStaleFinishedAtClearsItBeforeTheTransition() {
        final var entity = jobRunEntity(JobRunState.BUSY, Instant.now().minusSeconds(60));
        when(jobRunRepository.findById(JOB_RUN_ID)).thenReturn(Optional.of(entity));
        stubNoRaceTransition();

        jobDataService.updateJobRunState(JOB_RUN_ID, JobRunState.BUSY, Instant.now());

        // First save clears the stale finishedAt, second is the transition's own save.
        verify(jobRunRepository, times(2)).save(entity);
        assertNull(entity.finishedAt());
    }

    @Test
    void acknowledgingAnAcknowledgeableRunRecordsAnExtraTransitionBeforeFinishing() {
        final var entity = jobRunEntity(JobRunState.ERROR, null);
        when(jobRunRepository.findById(JOB_RUN_ID)).thenReturn(Optional.of(entity));
        stubNoRaceTransition();

        jobDataService.updateJobRunState(JOB_RUN_ID, JobRunState.ACKNOWLEDGED, Instant.now());

        // One transition row for the ACKNOWLEDGED-while-acknowledgeable branch, one for the
        // method's own final transition to ACKNOWLEDGED.
        verify(jobRunStateTransitionRepository, times(2)).save(any());
    }

    @Test
    void acknowledgingANonAcknowledgeableRunOnlyRecordsTheFinalTransition() {
        final var entity = jobRunEntity(JobRunState.DONE, null);
        when(jobRunRepository.findById(JOB_RUN_ID)).thenReturn(Optional.of(entity));
        stubNoRaceTransition();

        jobDataService.updateJobRunState(JOB_RUN_ID, JobRunState.ACKNOWLEDGED, Instant.now());

        verify(jobRunStateTransitionRepository, times(1)).save(any());
    }

    @Test
    void anAlreadyFinishedRunIsNotModifiedByAnotherFinishedState() {
        final var alreadyFinishedAt = Instant.now().minusSeconds(120);
        final var entity = jobRunEntity(JobRunState.DONE, alreadyFinishedAt);
        when(jobRunRepository.findById(JOB_RUN_ID)).thenReturn(Optional.of(entity));

        jobDataService.updateJobRunState(JOB_RUN_ID, JobRunState.ERROR, Instant.now());

        assertEquals(alreadyFinishedAt, entity.finishedAt());
        verify(jobRunRepository, never()).save(any());
        verifyNoInteractions(jobRunStateTransitionRepository);
    }

    @Test
    void transitioningToAFinishedStateSetsFinishedAt() {
        final var entity = jobRunEntity(JobRunState.BUSY, null);
        when(jobRunRepository.findById(JOB_RUN_ID)).thenReturn(Optional.of(entity));
        stubNoRaceTransition();
        final var transitionedAt = Instant.now();

        jobDataService.updateJobRunState(JOB_RUN_ID, JobRunState.DONE, transitionedAt);

        assertEquals(transitionedAt, entity.finishedAt());
        assertEquals(JobRunState.DONE, entity.latestState());
    }

    @Test
    void transitioningToARunningStateClearsFinishedAt() {
        final var entity = jobRunEntity(JobRunState.SCHEDULED, null);
        when(jobRunRepository.findById(JOB_RUN_ID)).thenReturn(Optional.of(entity));
        stubNoRaceTransition();

        jobDataService.updateJobRunState(JOB_RUN_ID, JobRunState.BUSY, Instant.now());

        assertNull(entity.finishedAt());
        assertEquals(JobRunState.BUSY, entity.latestState());
    }

    @Test
    void aConcurrentlySavedNewerTransitionWinsOverTheOneJustRequested() {
        final var entity = jobRunEntity(JobRunState.BUSY, null);
        when(jobRunRepository.findById(JOB_RUN_ID)).thenReturn(Optional.of(entity));
        final var racingTransition = mock(JobRunStateTransitionEntity.class);
        when(racingTransition.state()).thenReturn(JobRunState.ERROR);
        when(jobRunStateTransitionRepository.findFirstByJobRunIdOrderByTransitionedAtDesc(
                        JOB_RUN_ID))
                .thenReturn(racingTransition);

        jobDataService.updateJobRunState(JOB_RUN_ID, JobRunState.DONE, Instant.now());

        // The race-corrected state (ERROR) wins over what this call requested (DONE).
        assertEquals(JobRunState.ERROR, entity.latestState());
    }

    private void stubNoRaceTransition() {
        when(jobRunStateTransitionRepository.findFirstByJobRunIdOrderByTransitionedAtDesc(
                        JOB_RUN_ID))
                .thenReturn(null);
    }

    private static JobRunEntity jobRunEntity(
            final JobRunState latestState, final @Nullable Instant finishedAt) {
        final var entity =
                new JobRunEntity(
                        1, "1.0.0", JobRunnerKey.CURRENT, Instant.now(), new JobRunArguments());
        ReflectionTestUtils.setField(entity, "id", JOB_RUN_ID);
        entity.setLatestState(latestState);
        entity.setFinishedAt(finishedAt);
        return entity;
    }
}
