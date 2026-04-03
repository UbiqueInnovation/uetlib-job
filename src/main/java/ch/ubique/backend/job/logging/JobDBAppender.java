package ch.ubique.backend.job.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.service.logs.LogFormatterService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class JobDBAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

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
        final var mdc = event.getMDCPropertyMap();
        if (mdc == null || !mdc.containsKey("jobRunId")) {
            return;
        }

        final var jobRunId = Integer.parseInt(mdc.get("jobRunId"));
        final var formattedMessage = logFormatterService.toFormattedMessage(event);
        logQueue.add(new JobRunLoggingEvent(jobRunId, event.getInstant(), formattedMessage));
    }

    private synchronized void writeQueueToDB() {
        while (true) {
            drainedEvents.clear();
            logQueue.drainTo(drainedEvents, BATCH_SIZE);

            if (drainedEvents.isEmpty()) {
                return;
            }

            jobDataService.insertLogEvents(drainedEvents);

            if (drainedEvents.size() < BATCH_SIZE) {
                return;
            }
        }
    }

    @Override
    public void start() {
        super.start();
        scheduler.scheduleWithFixedDelay(this::writeQueueToDB, 0, 3, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        super.stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (final InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        writeQueueToDB();
    }
}
