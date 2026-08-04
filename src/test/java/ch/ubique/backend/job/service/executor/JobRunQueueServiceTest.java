package ch.ubique.backend.job.service.executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobQueuedRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.queue.JobRunQueueState;
import ch.ubique.backend.job.model.runner.conditions.JobRunnerTaint;
import ch.ubique.backend.job.model.runner.conditions.JobRunnerTaints;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.info.JobRunnerInfoService;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Taint subset-matching used to live in a JSON_TABLE-based SQL query; it is now Java-side logic
 * ({@link JobRunQueueService#matchesTaints}) exercised here via the dedicated-runner path, which is
 * the simplest one to drive with mocks (it skips the runner-group capacity check that the plain
 * path needs). Also covers the paging strategy and the no-concurrent-execution guard.
 */
class JobRunQueueServiceTest {

    private static final String INSTANCE_NAME = "test-instance";
    private static final int PAGE_SIZE = 2;

    private final JobService jobService = mock(JobService.class);
    private final JobRunnerInfoService jobRunnerInfoService = mock(JobRunnerInfoService.class);
    private final JobExecutorService jobExecutorService = mock(JobExecutorService.class);

    private final JobRunQueueService service =
            new JobRunQueueService(
                    jobService,
                    jobRunnerInfoService,
                    jobExecutorService,
                    INSTANCE_NAME,
                    List.of("gpu"),
                    PAGE_SIZE);

    @Test
    void keepsPagingUntilAPageHasAMatch() {
        final var nonMatching = queuedRun(1, Set.of(new JobRunnerTaint("unadvertised")));
        final var matching =
                queuedRun(2, Set.of(JobRunnerTaints.DEDICATED_K8S_TAINT, new JobRunnerTaint("gpu")));
        stubDedicatedClaimAttempt();
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(new SliceImpl<>(List.of(nonMatching), PageRequest.of(0, PAGE_SIZE), true));
        when(jobService.findQueuedRunsPage(PageRequest.of(1, PAGE_SIZE)))
                .thenReturn(new SliceImpl<>(List.of(matching), PageRequest.of(1, PAGE_SIZE), false));

        service.checkForRunnableDedicatedAndSchedule();

        verify(jobService).findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE));
        verify(jobService).findQueuedRunsPage(PageRequest.of(1, PAGE_SIZE));
        verify(jobService).claimQueuedRun(matching, INSTANCE_NAME);
        verify(jobService, never()).claimQueuedRun(nonMatching, INSTANCE_NAME);
    }

    @Test
    void stopsPagingAsSoonAsAPageHasAMatch() {
        final var matching = queuedRun(1, Set.of(JobRunnerTaints.DEDICATED_K8S_TAINT));
        stubDedicatedClaimAttempt();
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(new SliceImpl<>(List.of(matching), PageRequest.of(0, PAGE_SIZE), true));

        service.checkForRunnableDedicatedAndSchedule();

        verify(jobService).findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE));
        verify(jobService, never()).findQueuedRunsPage(PageRequest.of(1, PAGE_SIZE));
    }

    @Test
    void stopsAtTheLastPageWhenNothingEverMatches() {
        final var nonMatching = queuedRun(1, Set.of(new JobRunnerTaint("unadvertised")));
        when(jobService.findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(new SliceImpl<>(List.of(nonMatching), PageRequest.of(0, PAGE_SIZE), false));

        service.checkForRunnableDedicatedAndSchedule();

        verify(jobService).findQueuedRunsPage(PageRequest.of(0, PAGE_SIZE));
        verify(jobService, never()).claimQueuedRun(any(), any());
    }

    @Test
    void aConcurrentInvocationIsSkippedRatherThanRunInParallel() {
        final var run = queuedRun(1, Set.of(JobRunnerTaints.DEDICATED_K8S_TAINT));
        stubDedicatedClaimAttempt();
        // Simulates a second scheduled tick arriving while the first check is still in progress:
        // re-entering from inside the mocked call, on the same thread, hits the exact same
        // AtomicBoolean guard a genuinely concurrent invocation would.
        when(jobService.findQueuedRunsPage(any()))
                .thenAnswer(
                        invocation -> {
                            service.checkForRunnableDedicatedAndSchedule();
                            return new SliceImpl<>(List.of(run), PageRequest.of(0, PAGE_SIZE), false);
                        });

        service.checkForRunnableDedicatedAndSchedule();

        verify(jobService, times(1)).findQueuedRunsPage(any());
    }

    private void stubDedicatedClaimAttempt() {
        when(jobService.findJobById(anyInt()))
                .thenReturn(Optional.of(new JobClassAndId(1, "ch.ubique.backend.job.TestJob")));
        when(jobService.hasRunner(JobRunnerKey.DEDICATED_K8S_JOB)).thenReturn(true);
        // Claim "fails" (already claimed elsewhere) so the test doesn't need to also stub a full
        // successful submission through JobExecutorService - only that a claim was attempted.
        when(jobService.claimQueuedRun(any(), any())).thenReturn(false);
    }

    private static JobQueuedRun queuedRun(final int id, final Set<JobRunnerTaint> taints) {
        final var now = Instant.now();
        return new JobQueuedRun(
                id,
                null,
                1,
                "1.0.0",
                JobRunQueueState.QUEUED,
                null,
                null,
                null,
                now,
                now,
                taints,
                new JobRunArguments());
    }
}
