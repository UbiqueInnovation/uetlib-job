package ch.ubique.backend.job.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.service.executor.runners.DedicatedJobRunner;
import ch.ubique.backend.job.service.logs.LogFormatterService;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class JobDBAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    private final JobDataService jobDataService;
    private final LogFormatterService logFormatterService;

    private final @Nullable DedicatedJobRunner dedicatedJobRunner;

    public JobDBAppender(
            final JobDataService jobDataService,
            final LogFormatterService logFormatterService,
            @Nullable final DedicatedJobRunner dedicatedJobRunner) {
        this.jobDataService = jobDataService;
        this.logFormatterService = logFormatterService;
        this.dedicatedJobRunner = dedicatedJobRunner;
    }

    @Override
    protected void append(final ILoggingEvent iLoggingEvent) {
        final var jobRunId = getJobRunId(iLoggingEvent);
        if (jobRunId == null) {
            return;
        }
        final var formattedMessage = logFormatterService.toFormattedMessage(iLoggingEvent);
        jobDataService.insertLogEvent(jobRunId, iLoggingEvent.getInstant(), formattedMessage);
    }

    private @Nullable Integer getJobRunId(final ILoggingEvent iLoggingEvent) {
        if (dedicatedJobRunner != null && dedicatedJobRunner.activeJobRun() != null) {
            return dedicatedJobRunner.activeJobRun().id();
        }
        final var runId = iLoggingEvent.getMDCPropertyMap().get("jobRunId");
        if (runId == null) {
            return null;
        }
        return Integer.parseInt(runId);
    }
}
