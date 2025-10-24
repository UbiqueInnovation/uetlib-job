package ch.ubique.backend.job.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.service.logs.LogFormatterService;

public class JobDBAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    private final JobDataService jobDataService;
    private final LogFormatterService logFormatterService;

    public JobDBAppender(
            JobDataService jobDataService, LogFormatterService logFormatterService) {
        this.jobDataService = jobDataService;
        this.logFormatterService = logFormatterService;
    }

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        var jobRunId = Integer.parseInt(iLoggingEvent.getMDCPropertyMap().get("jobRunId"));
        var formattedMessage = logFormatterService.toFormattedMessage(iLoggingEvent);
        jobDataService.insertLogEvent(jobRunId, iLoggingEvent.getInstant(), formattedMessage);
    }
}
