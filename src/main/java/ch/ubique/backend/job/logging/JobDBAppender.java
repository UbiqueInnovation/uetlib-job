package ch.ubique.backend.job.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.service.logs.LogFormatterService;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class JobDBAppender extends UnsynchronizedAppenderBase<ILoggingEvent>
        implements DisposableBean {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final int BATCH_SIZE = 1_000;
    private final JobDataService jobDataService;
    private final LogFormatterService logFormatterService;

    private final BlockingQueue<JobRunLoggingEvent> logQueue = new LinkedBlockingQueue<>();
    private final List<JobRunLoggingEvent> drainedEvents = new ArrayList<>(BATCH_SIZE);
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        var thread = new Thread(r, "job-db-logs");
                        thread.setDaemon(true);
                        return thread;
                    });

    public JobDBAppender(
            final JobDataService jobDataService, final LogFormatterService logFormatterService) {
        this.jobDataService = jobDataService;
        this.logFormatterService = logFormatterService;
    }

    @Override
    protected void append(final ILoggingEvent event) {
        final var jobRunId = getJobRunId(event);
        if (jobRunId == null) {
            return;
        }
        final var formattedMessage = logFormatterService.toFormattedMessage(event);
        logQueue.add(new JobRunLoggingEvent(jobRunId, event.getInstant(), formattedMessage));
    }

    private static @Nullable Integer getJobRunId(final ILoggingEvent event) {
        final var mdc = event.getMDCPropertyMap();
        if (mdc == null || !mdc.containsKey("jobRunId")) {
            return null;
        }
        try {
            return Integer.parseInt(mdc.get("jobRunId"));
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private synchronized void writeQueueToDB() {
        while (true) {
            drainedEvents.clear();
            logQueue.drainTo(drainedEvents, BATCH_SIZE);

            if (drainedEvents.isEmpty()) {
                return;
            }

            final var success = tryToInsert();
            if (!success) {
                return;
            }

            if (drainedEvents.size() < BATCH_SIZE) {
                return;
            }
        }
    }

    private boolean tryToInsert() {
        try {
            jobDataService.insertLogEvents(drainedEvents);
            return true;
        } catch (final Exception e) {
            logQueue.addAll(drainedEvents);
            logger.warn("Failed to insert log events to database.", e);
            return false;
        }
    }

    @Override
    public void start() {
        super.start();
        scheduler.scheduleWithFixedDelay(this::writeQueueToDB, 0, 5, TimeUnit.SECONDS);
    }

    @Override
    @EventListener(ContextClosedEvent.class)
    public void stop() {
        super.stop();
        stopScheduler();
        writeQueueToDB();
    }

    private void stopScheduler() {
        if (scheduler.isShutdown()) {
            return;
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (final InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void destroy() {
        stopScheduler();
        writeQueueToDB();
    }
}
