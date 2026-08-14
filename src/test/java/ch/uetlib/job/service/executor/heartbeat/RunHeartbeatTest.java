package ch.uetlib.job.service.executor.heartbeat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobRunContext;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.testjob.TestJob;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/**
 * Covers {@link RunHeartbeat} without waiting on its real (15s) schedule: {@link RunHeartbeat#tick}
 * is package-visible so ticks can be triggered manually. Heartbeats are created via {@link
 * HeartbeatRegistry#start}, the same entry point production code uses, rather than constructing
 * {@link RunHeartbeat} directly.
 */
class RunHeartbeatTest {

    private static final int JOB_RUN_ID = 42;

    private final JobService jobService = mock(JobService.class);
    private final JobRunContext jobRunContext = jobRunContext();
    private final HeartbeatRegistry registry = new HeartbeatRegistry();

    @Test
    void onLocalExecutionFinishedWithATerminalStateStopsImmediately() {
        final var heartbeat = registry.start(jobService, jobRunContext);

        heartbeat.onLocalExecutionFinished(JobRunState.DONE);

        assertTrue(heartbeat.isStopped());
    }

    @Test
    void onLocalExecutionFinishedWithScheduledSwitchesToWatchingTheRunsOwnState() {
        final var heartbeat = registry.start(jobService, jobRunContext);

        heartbeat.onLocalExecutionFinished(JobRunState.SCHEDULED);

        assertFalse(heartbeat.isStopped());
        assertInstanceOf(ScheduledElsewhereCheckIn.class, heartbeat.currentCheckIn());
    }

    @Test
    void scheduledElsewhereTouchesTheHeartbeatWhileTheRunIsStillScheduled() {
        final var heartbeat = registry.start(jobService, jobRunContext);
        heartbeat.onLocalExecutionFinished(JobRunState.SCHEDULED);
        when(jobService.getJobRunMaybe(JOB_RUN_ID))
                .thenReturn(Optional.of(jobRun(JobRunState.SCHEDULED)));

        heartbeat.tick();

        verify(jobService).touchRunningHeartbeat(JOB_RUN_ID);
        assertFalse(heartbeat.isStopped());
    }

    @Test
    void scheduledElsewhereStopsOnceTheRunMovesOnToAnotherState() {
        final var heartbeat = registry.start(jobService, jobRunContext);
        heartbeat.onLocalExecutionFinished(JobRunState.SCHEDULED);
        when(jobService.getJobRunMaybe(JOB_RUN_ID))
                .thenReturn(Optional.of(jobRun(JobRunState.DONE)));

        heartbeat.tick();

        verify(jobService, never()).touchRunningHeartbeat(anyInt());
        assertTrue(heartbeat.isStopped());
    }

    @Test
    void scheduledElsewhereStopsWhenTheRunNoLongerExists() {
        final var heartbeat = registry.start(jobService, jobRunContext);
        heartbeat.onLocalExecutionFinished(JobRunState.SCHEDULED);
        when(jobService.getJobRunMaybe(JOB_RUN_ID)).thenReturn(Optional.empty());

        heartbeat.tick();

        verify(jobService, never()).touchRunningHeartbeat(anyInt());
        assertTrue(heartbeat.isStopped());
    }

    @Test
    void threadProgressTouchesTheHeartbeatWhileTheWorkerThreadIsMakingProgress() {
        final var heartbeat = registry.start(jobService, jobRunContext);
        burnSomeCpu();

        heartbeat.tick();

        verify(jobService).touchRunningHeartbeat(JOB_RUN_ID);
        assertFalse(heartbeat.isStopped());
    }

    @Test
    void threadProgressCancelsTheRunWhenTheWorkerThreadDiedWithoutCompletingNormally()
            throws Exception {
        final var heartbeatRef = new RunHeartbeat[1];
        final var constructed = new CountDownLatch(1);
        final var worker =
                new Thread(
                        () -> {
                            heartbeatRef[0] = registry.start(jobService, jobRunContext);
                            constructed.countDown();
                        });
        worker.start();
        constructed.await();
        worker.join();

        heartbeatRef[0].tick();

        verify(jobService)
                .updateJobRunState(eq(jobRunContext.jobRun()), eq(JobRunState.CANCELLED), any());
        verify(jobService, never()).touchRunningHeartbeat(anyInt());
        assertTrue(heartbeatRef[0].isStopped());
    }

    @Test
    void threadProgressTouchesTheHeartbeatForAVirtualWorkerThreadDespiteUnsupportedCpuTime()
            throws Exception {
        // getThreadCpuTime() is unconditionally -1 for a virtual thread, which would otherwise
        // compare equal to itself forever and look like zero progress no matter how much real work
        // happens; CheckInState must fall back to isAlive()-only for this case instead.
        final var heartbeatRef = new RunHeartbeat[1];
        final var constructed = new CountDownLatch(1);
        final var releaseWorker = new CountDownLatch(1);
        final var worker =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    heartbeatRef[0] = registry.start(jobService, jobRunContext);
                                    constructed.countDown();
                                    try {
                                        releaseWorker.await();
                                    } catch (final InterruptedException _) {
                                        // ignored
                                    }
                                });
        constructed.await();

        heartbeatRef[0].tick();

        verify(jobService).touchRunningHeartbeat(JOB_RUN_ID);
        assertFalse(heartbeatRef[0].isStopped());
        releaseWorker.countDown();
        worker.join();
    }

    @Test
    void registryStopCancelsAndForgetsATrackedHeartbeat() {
        final var heartbeat = registry.start(jobService, jobRunContext);

        registry.stop(JOB_RUN_ID);

        assertTrue(heartbeat.isStopped());
        // Idempotent: nothing tracked anymore, calling stop again must not throw.
        assertDoesNotThrow(() -> registry.stop(JOB_RUN_ID));
    }

    /** Guarantees a measurable, non-flaky CPU time delta between two check-ins. */
    private static void burnSomeCpu() {
        var hash = 0;
        for (var i = 0; i < 5_000_000; i++) {
            hash = hash * 31 + i;
        }
        assertNotEquals(Integer.MIN_VALUE + 1, hash); // prevents the loop from being optimized away
    }

    private static JobRunContext jobRunContext() {
        return new JobRunContext(
                new JobClassAndId(1, TestJob.class),
                jobRun(JobRunState.BUSY),
                new JobRunArguments(),
                new JobProperties(List.of()));
    }

    private static JobRun jobRun(final JobRunState state) {
        return new JobRun(
                JOB_RUN_ID,
                1,
                "1.0.0",
                Instant.now(),
                null,
                state,
                JobRunnerKey.CURRENT,
                null,
                null);
    }
}
