package ch.ubique.backend.job.logging;

import ch.qos.logback.classic.db.DBAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public class JobAppender extends AppenderBase<ILoggingEvent> {

    private final DBAppender dbAppender;

    public JobAppender(final DBAppender dbAppender) {
        this.dbAppender = dbAppender;
    }

    @Override
    public void start() {
        dbAppender.start();
        super.start();
    }

    @Override
    public void stop() {
        dbAppender.stop();
        super.stop();
    }

    @Override
    protected void append(final ILoggingEvent eventObject) {
        final var jobRunId = eventObject.getMDCPropertyMap().get("jobRunId");
        if (jobRunId == null) {
            return;
        }

        dbAppender.append(eventObject);
    }
}
