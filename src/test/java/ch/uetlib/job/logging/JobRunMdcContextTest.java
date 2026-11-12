package ch.uetlib.job.logging;

import static org.junit.jupiter.api.Assertions.*;

import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Instant;

class JobRunMdcContextTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void putsTheJobRunIdOnTheMdcWhileOpenAndRestoresItOnClose() {
        MDC.put("preexisting", "value");

        try (final var _ = new JobRunMdcContext(42)) {
            assertEquals("42", MDC.get("jobRunId"));
            assertEquals("value", MDC.get("preexisting"));
        }

        assertNull(MDC.get("jobRunId"));
        assertEquals("value", MDC.get("preexisting"));
    }

    @Test
    void constructingFromAJobRunUsesItsId() {
        final var jobRun =
                new JobRun(
                        7,
                        1,
                        "1.0.0",
                        Instant.now(),
                        null,
                        JobRunState.BUSY,
                        JobRunnerKey.CURRENT,
                        null,
                        new JobRunArguments());

        try (final var _ = new JobRunMdcContext(jobRun)) {
            assertEquals("7", MDC.get("jobRunId"));
        }
    }

    @Test
    void runWithJobRunMdcContextRunsTheRunnableWithTheContextActive() {
        final var jobRun =
                new JobRun(
                        9,
                        1,
                        "1.0.0",
                        Instant.now(),
                        null,
                        JobRunState.BUSY,
                        JobRunnerKey.CURRENT,
                        null,
                        new JobRunArguments());
        final var observedJobRunId = new String[1];

        JobRunMdcContext.runWithJobRunMdcContext(
                jobRun, () -> observedJobRunId[0] = MDC.get("jobRunId"));

        assertEquals("9", observedJobRunId[0]);
        assertNull(MDC.get("jobRunId"));
    }
}
