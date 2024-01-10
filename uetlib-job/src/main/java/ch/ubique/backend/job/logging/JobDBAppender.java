package ch.ubique.backend.job.logging;

import ch.qos.logback.classic.db.DBAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class JobDBAppender extends DBAppender {

    @Override
    public void append(final ILoggingEvent eventObject) {
        final var jobRunId = eventObject.getMDCPropertyMap().get("jobRunId");
        if (jobRunId == null) {
            return;
        }

        super.append(eventObject);
    }
}
