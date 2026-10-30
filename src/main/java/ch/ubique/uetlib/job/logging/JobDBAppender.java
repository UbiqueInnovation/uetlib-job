package ch.ubique.uetlib.job.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.ubique.uetlib.job.data.service.JobDataService;
import ch.ubique.uetlib.job.service.executor.runners.DedicatedJobRunner;
import ch.ubique.uetlib.job.service.logs.LogFormatterService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.dao.TransientDataAccessException;

public class JobDBAppender extends UnsynchronizedAppenderBase<ILoggingEvent>
        implements DisposableBean {

    private static final String DB_APPENDER_THREAD_NAME = "job-db-logs";
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final int BATCH_SIZE = 1_000;
    private final JobDataService jobDataService;
    private final LogFormatterService logFormatterService;

    private final BlockingQueue<JobRunLoggingEvent> logQueue = new LinkedBlockingQueue<>();
    private final List<JobRunLoggingEvent> drainedEvents = new ArrayList<>(BATCH_SIZE);
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        var thread = new Thread(r, DB_APPENDER_THREAD_NAME);
                        thread.setDaemon(true);
                        return thread;
                    });

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
    protected void append(final ILoggingEvent event) {
        if (DB_APPENDER_THREAD_NAME.equals(Thread.currentThread().getName())) {
            // Do not process logs from the DB appender thread itself
            return;
        }

        final var jobRunId = getJobRunId(event);
        if (jobRunId == null) {
            return;
        }
        final var formattedMessage = logFormatterService.toFormattedMessage(event);
        logQueue.add(new JobRunLoggingEvent(jobRunId, event.getInstant(), formattedMessage));
    }

    private @Nullable Integer getJobRunId(final ILoggingEvent event) {
        if (dedicatedJobRunner != null && dedicatedJobRunner.activeJobRun() != null) {
            return dedicatedJobRunner.activeJobRun().id();
        }
        final var mdc = event.getMDCPropertyMap();
        if (mdc == null || !mdc.containsKey("jobRunId")) {
            return null;
        }
        try {
            return Integer.parseInt(mdc.get("jobRunId"));
        } catch (final NumberFormatException _) {
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
        } catch (final TransientDataAccessException e) {
            logQueue.addAll(drainedEvents);
            logger.warn("Failed to insert log events to database (will retry).", e);
            return false;
        } catch (final NonTransientDataAccessException _) {
            return tryInsertIndividually();
        } catch (final Exception e) {
            logger.warn("Failed to insert log events to database.", e);
            return false;
        }
    }

    private boolean tryInsertIndividually() {
        final var retryEvents = new ArrayList<JobRunLoggingEvent>();
        Exception warnRetryException = null;
        Exception warnNoretryException = null;
        var anySuccess = false;
        for (var logEvent : drainedEvents) {
            try {
                jobDataService.insertLogEvents(List.of(logEvent));
                anySuccess = true;
            } catch (final TransientDataAccessException e) {
                retryEvents.add(logEvent);
                if (warnRetryException == null) {
                    warnRetryException = e;
                } else {
                    warnRetryException.addSuppressed(e);
                }
            } catch (final Exception e) { // NonTransientDataAccessException or anything else
                if (warnNoretryException == null) {
                    warnNoretryException = e;
                } else {
                    warnNoretryException.addSuppressed(e);
                }
            }
        }
        if (warnNoretryException != null) {
            logger.warn("Failed to insert log events to database.", warnNoretryException);
        }
        if (warnRetryException != null) {
            logger.warn(
                    "Failed to insert log events to database (will retry).", warnRetryException);
        }
        logQueue.addAll(retryEvents);
        return anySuccess;
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
        } catch (final InterruptedException _) {
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
