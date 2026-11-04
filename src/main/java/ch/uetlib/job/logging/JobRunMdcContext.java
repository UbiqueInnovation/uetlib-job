package ch.uetlib.job.logging;

import ch.uetlib.job.model.JobRun;

import org.slf4j.MDC;

import java.util.Map;

public class JobRunMdcContext implements AutoCloseable {

    private final Map<String, String> previousContextMap;

    public JobRunMdcContext(final JobRun jobRun) {
        this(jobRun.id());
    }

    public JobRunMdcContext(final int jobRunId) {
        previousContextMap = MDC.getCopyOfContextMap();
        MDC.put("jobRunId", String.valueOf(jobRunId));
    }

    public static void runWithJobRunMdcContext(final JobRun jobRun, final Runnable runnable) {
        try (final var _ = new JobRunMdcContext(jobRun)) {
            runnable.run();
        }
    }

    @Override
    public void close() {
        MDC.setContextMap(previousContextMap);
    }
}
