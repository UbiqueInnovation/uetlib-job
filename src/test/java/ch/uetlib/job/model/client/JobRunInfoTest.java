package ch.uetlib.job.model.client;

import static org.junit.jupiter.api.Assertions.*;

import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.RetryTriggerContext;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.triggers.TriggerType;

import org.junit.jupiter.api.Test;

import java.time.Instant;

class JobRunInfoTest {

    @Test
    void fromAFinishedRunUsesItsOwnFinishedAtForTheDuration() {
        final var startedAt = Instant.now().minusSeconds(60);
        final var finishedAt = Instant.now().minusSeconds(10);
        final var jobRun =
                new JobRun(
                        1,
                        2,
                        "1.0.0",
                        startedAt,
                        finishedAt,
                        JobRunState.DONE,
                        JobRunnerKey.CURRENT,
                        TriggerType.CRON,
                        new JobRunArguments());

        final var info = JobRunInfo.from(jobRun, true);

        assertEquals(1, info.id());
        assertEquals(2, info.jobId());
        assertEquals(JobRunState.DONE, info.state());
        assertEquals(finishedAt, info.finishedAt());
        assertEquals(TriggerType.CRON, info.triggerType());
        assertTrue(info.hasFiles());
        assertNull(info.triggerContext());
    }

    @Test
    void fromAStillRunningRunHasNoFinishedAt() {
        final var jobRun =
                new JobRun(
                        1,
                        2,
                        "1.0.0",
                        Instant.now(),
                        null,
                        JobRunState.BUSY,
                        JobRunnerKey.CURRENT,
                        null,
                        new JobRunArguments());

        final var info = JobRunInfo.from(jobRun, false);

        assertNull(info.finishedAt());
        assertNull(info.triggerType());
        assertFalse(info.hasFiles());
    }

    @Test
    void fromExposesTheTriggerContextWhenPresent() {
        final var context = new RetryTriggerContext(1, 3, 42);
        final var jobRun =
                new JobRun(
                        1,
                        2,
                        "1.0.0",
                        Instant.now(),
                        null,
                        JobRunState.BUSY,
                        JobRunnerKey.CURRENT,
                        TriggerType.RETRY,
                        new JobRunArguments(false, TriggerType.RETRY, context, null, null));

        final var info = JobRunInfo.from(jobRun, false);

        assertEquals(context, info.triggerContext());
    }
}
