package ch.uetlib.job.service.executor.heartbeat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Instant;

/**
 * The bookkeeping a {@link ThreadProgressCheckIn} needs between ticks: the worker thread being
 * watched, the {@link ThreadMXBean} used to read its CPU time, whether that's even supported for
 * this worker thread, the CPU time observed at the last check-in, and when that check-in happened.
 */
record CheckInState(
        Thread workerThread,
        ThreadMXBean threadMxBean,
        boolean cpuTimeSupported,
        long lastCpuTime,
        Instant lastCheckInAt) {
    private static final Logger logger = LoggerFactory.getLogger(CheckInState.class);

    static {
        final var threadMxBean = ManagementFactory.getThreadMXBean();
        if (!threadMxBean.isThreadCpuTimeSupported()) {
            logger.info(
                    "Per-Thread CPU Time Measurement is not supported. "
                            + "Heartbeats for Java Jobs will always return running and cannot be expected to detect stuck jobs.");
        }
        if (threadMxBean.isThreadCpuTimeSupported() && !threadMxBean.isThreadCpuTimeEnabled()) {
            threadMxBean.setThreadCpuTimeEnabled(true);
        }
    }

    static CheckInState start(final Thread workerThread) {
        final var threadMxBean = ManagementFactory.getThreadMXBean();
        if (workerThread.isVirtual()) {
            // getThreadCpuTime(long) unconditionally returns -1 for a virtual thread
            // (JDK-confirmed,
            // not just "may be unsupported"), so treating it as supported would compare -1 to -1
            // forever and look like zero progress no matter how much real work the thread is doing.
            // Runner-group executors (CurrentJobRunner, DedicatedJobRunner) are built to never hand
            // a
            // job's main thread a virtual one; this is a defensive fallback in case that ever
            // changes.
            logger.warn(
                    "Worker thread {} for a job run is a virtual thread; per-thread CPU time isn't"
                            + " supported for virtual threads, so its heartbeat can only prove"
                            + " liveness (isAlive()), not actual progress.",
                    workerThread.getName());
        }
        final var cpuTimeSupported =
                threadMxBean.isThreadCpuTimeSupported() && !workerThread.isVirtual();
        return new CheckInState(
                workerThread,
                threadMxBean,
                cpuTimeSupported,
                cpuTime(threadMxBean, workerThread, cpuTimeSupported),
                Instant.now());
    }

    long currentCpuTime() {
        return cpuTime(threadMxBean, workerThread, cpuTimeSupported);
    }

    CheckInState checkedIn(final long cpuTime) {
        return new CheckInState(
                workerThread, threadMxBean, cpuTimeSupported, cpuTime, Instant.now());
    }

    /** {@code -1} if per-thread CPU time isn't supported on this JVM, or the thread is virtual. */
    private static long cpuTime(
            final ThreadMXBean threadMxBean, final Thread thread, final boolean supported) {
        return supported ? threadMxBean.getThreadCpuTime(thread.threadId()) : -1;
    }
}
