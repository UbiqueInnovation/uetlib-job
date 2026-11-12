package ch.uetlib.job.service.executor.runners;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.Job;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobRunContext;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.service.JobService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

/** Covers this package-private helper's core success/exception/interrupt paths directly. */
class JobRunnerHelperTest {

    private static final JobClassAndId JOB = new JobClassAndId(1, "ch.uetlib.job.TestJob");

    private final JobService jobService = mock(JobService.class);

    @Test
    void prepareRunStartsTheRunAndCarriesOverEverythingElse() {
        final var initialContext = jobRunContext(JobRunState.SCHEDULED);
        final var started = jobRun(JobRunState.BUSY);
        when(jobService.startJobRun(eq(initialContext.jobRun()), any(Instant.class)))
                .thenReturn(started);

        final var result = JobRunnerHelper.prepareRun(jobService, initialContext);

        assertEquals(started, result.jobRun());
        assertEquals(initialContext.job(), result.job());
        assertEquals(initialContext.arguments(), result.arguments());
        assertEquals(initialContext.properties(), result.properties());
    }

    @Test
    void runJobOnSuccessUpdatesState() {
        final var context = jobRunContext(JobRunState.BUSY);
        final var job = mock(Job.class);
        try {
            when(job.run(context)).thenReturn(JobRunState.DONE);
        } catch (final Exception e) {
            throw new AssertionError(e);
        }

        JobRunnerHelper.runJob(job, context, jobService);

        verify(jobService).updateJobRunState(eq(context.jobRun()), eq(JobRunState.DONE), any());
    }

    @Test
    void runJobOnAnUncaughtExceptionMarksTheRunAsError() {
        final var context = jobRunContext(JobRunState.BUSY);
        final var job = mock(Job.class);
        try {
            when(job.run(context)).thenThrow(new RuntimeException("boom"));
        } catch (final Exception e) {
            throw new AssertionError(e);
        }

        JobRunnerHelper.runJob(job, context, jobService);

        final var stateCaptor = ArgumentCaptor.forClass(JobRunState.class);
        verify(jobService).updateJobRunState(eq(context.jobRun()), stateCaptor.capture(), any());
        assertEquals(JobRunState.ERROR, stateCaptor.getValue());
    }

    @Test
    void runJobOnInterruptionMarksTheRunAsCancelledAndReinterruptsTheThread() {
        final var context = jobRunContext(JobRunState.BUSY);
        final var job = mock(Job.class);
        try {
            when(job.run(context)).thenThrow(new RuntimeException(new InterruptedException()));
        } catch (final Exception e) {
            throw new AssertionError(e);
        }

        JobRunnerHelper.runJob(job, context, jobService);

        try {
            verify(jobService)
                    .updateJobRunState(eq(context.jobRun()), eq(JobRunState.CANCELLED), any());
            assertTrue(Thread.interrupted());
        } finally {
            // Clear the interrupt flag regardless of assertion outcome, so it can't leak into
            // whichever test the runner picks next on this same thread.
            Thread.interrupted();
        }
    }

    @Test
    void updateJobRunStateDelegatesToJobService() {
        final var context = jobRunContext(JobRunState.BUSY);

        JobRunnerHelper.updateJobRunState(jobService, context, JobRunState.WARNING);

        verify(jobService).updateJobRunState(eq(context.jobRun()), eq(JobRunState.WARNING), any());
    }

    private static JobRunContext jobRunContext(final JobRunState state) {
        return new JobRunContext(
                JOB, jobRun(state), new JobRunArguments(), new JobProperties(List.of()));
    }

    private static JobRun jobRun(final JobRunState state) {
        return new JobRun(
                1,
                JOB.id(),
                "1.0.0",
                Instant.now(),
                null,
                state,
                JobRunnerKey.CURRENT,
                null,
                new JobRunArguments());
    }
}
