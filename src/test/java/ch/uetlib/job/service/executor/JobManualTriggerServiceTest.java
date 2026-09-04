package ch.uetlib.job.service.executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.service.JobService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

class JobManualTriggerServiceTest {

    private final JobService jobService = mock(JobService.class);
    private final JobManualTriggerService service = new JobManualTriggerService(jobService);

    private static final JobClassAndId JOB = new JobClassAndId(1, "ch.uetlib.job.TestJob");

    @Test
    void replayJobRunQueuesANewRunWithAReplayContextPointingAtTheOriginal() {
        final var jobRun = jobRun(5, new JobRunArguments());
        when(jobService.getJobRun(5)).thenReturn(jobRun);
        when(jobService.findJobById(JOB.id())).thenReturn(Optional.of(JOB));

        service.replayJobRun(5);

        final var captor = ArgumentCaptor.forClass(JobRunArguments.class);
        verify(jobService).tryQueueNewRun(eq(JOB), captor.capture());
        assertNotNull(captor.getValue().replayContext());
        assertEquals(5, captor.getValue().replayContext().originalRunId());
    }

    @Test
    void replayJobRunFallsBackToDefaultArgumentsWhenTheOriginalRunHasNone() {
        final var jobRun = jobRun(6, null);
        when(jobService.getJobRun(6)).thenReturn(jobRun);
        when(jobService.findJobById(JOB.id())).thenReturn(Optional.of(JOB));

        service.replayJobRun(6);

        verify(jobService).tryQueueNewRun(eq(JOB), any());
    }

    private static JobRun jobRun(final int id, final JobRunArguments arguments) {
        return new JobRun(
                id,
                JOB.id(),
                "1.0.0",
                Instant.now(),
                null,
                JobRunState.DONE,
                JobRunnerKey.CURRENT,
                null,
                arguments);
    }
}
