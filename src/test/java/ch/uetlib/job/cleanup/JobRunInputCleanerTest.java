package ch.uetlib.job.cleanup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.config.UetlibJobBaseConfig;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.storage.JobRunStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class JobRunInputCleanerTest {

    @Test
    void toleratesAnAlreadyMissingDirectory() throws IOException {
        final var storage = mock(JobRunStorage.class);
        final var job = mock(JobClassAndId.class);
        final var run = mock(JobRun.class);
        when(job.name()).thenReturn("job");
        when(run.readableId()).thenReturn("run");
        when(storage.deleteJobRunDirectory("job", "run")).thenReturn(false);

        assertDoesNotThrow(() -> new JobRunInputCleaner(storage).cleanInputFiles(job, run));

        verify(storage).deleteJobRunDirectory("job", "run");
    }

    @Test
    void toleratesAnIoFailureAndContinuesCleaningOtherRuns() throws IOException {
        final var storage = mock(JobRunStorage.class);
        final var job = mock(JobClassAndId.class);
        final var failedRun = mock(JobRun.class);
        final var nextRun = mock(JobRun.class);
        when(job.name()).thenReturn("job");
        when(failedRun.readableId()).thenReturn("failed");
        when(nextRun.readableId()).thenReturn("next");
        when(storage.deleteJobRunDirectory("job", "failed"))
                .thenThrow(new IOException("Storage unavailable"));
        when(storage.deleteJobRunDirectory("job", "next")).thenReturn(true);
        final var cleaner = new JobRunInputCleaner(storage);

        assertDoesNotThrow(() -> cleaner.cleanInputFiles(job, failedRun));
        assertDoesNotThrow(() -> cleaner.cleanInputFiles(job, nextRun));

        verify(storage).deleteJobRunDirectory("job", "failed");
        verify(storage).deleteJobRunDirectory("job", "next");
    }

    @Test
    void propagatesRuntimeFailuresAndReleasesTheConcurrencyPermit() throws Exception {
        final var storage = mock(JobRunStorage.class);
        final var job = mock(JobClassAndId.class);
        final var run = mock(JobRun.class);
        when(job.name()).thenReturn("job");
        when(run.readableId()).thenReturn("run");
        final var failure = new RuntimeException("Storage timeout");
        when(storage.deleteJobRunDirectory("job", "run")).thenThrow(failure).thenReturn(true);

        try (final var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "cleanup-test",
                                    Map.of("ch.uetlib.job.cleanup.run-input-concurrency", 1)));
            context.registerBean(JobRunStorage.class, () -> storage);
            context.register(UetlibJobBaseConfig.class);
            context.scan(JobRunInputCleaner.class.getPackageName());
            context.refresh();
            final var cleaner = context.getBean(JobRunInputCleaner.class);

            assertSame(
                    failure,
                    assertThrows(RuntimeException.class, () -> cleaner.cleanInputFiles(job, run)));
            try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                final var next = executor.submit(() -> cleaner.cleanInputFiles(job, run));
                try {
                    next.get(5, TimeUnit.SECONDS);
                } finally {
                    next.cancel(true);
                }
            }
        }
        verify(storage, times(2)).deleteJobRunDirectory("job", "run");
    }

    @ParameterizedTest
    @ValueSource(ints = {32, 4})
    void limitsConcurrentStorageDeletionsThroughTheSpringProxy(final int limit) throws Exception {
        final var storage = mock(JobRunStorage.class);
        final var active = new AtomicInteger();
        final var maximum = new AtomicInteger();
        final var firstWave = new CountDownLatch(limit);
        final var overflow = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        when(storage.deleteJobRunDirectory("job", "run"))
                .thenAnswer(_ -> {
                    final var concurrent = active.incrementAndGet();
                    maximum.accumulateAndGet(concurrent, Math::max);
                    firstWave.countDown();
                    if (concurrent > limit) {
                        overflow.countDown();
                    }
                    try {
                        assertTrue(release.await(10, TimeUnit.SECONDS));
                        return true;
                    } finally {
                        active.decrementAndGet();
                    }
                });
        final var job = mock(JobClassAndId.class);
        final var run = mock(JobRun.class);
        when(job.name()).thenReturn("job");
        when(run.readableId()).thenReturn("run");

        try (final var context = new AnnotationConfigApplicationContext()) {
            // Leave the property unset for 32 to verify the default as well as an override.
            if (limit != 32) {
                context.getEnvironment()
                        .getPropertySources()
                        .addFirst(
                                new MapPropertySource(
                                        "cleanup-test",
                                        Map.of(
                                                "ch.uetlib.job.cleanup.run-input-concurrency",
                                                limit)));
            }
            context.registerBean(JobRunStorage.class, () -> storage);
            context.register(UetlibJobBaseConfig.class);
            context.scan(JobRunInputCleaner.class.getPackageName());
            context.refresh();
            final var cleaner = context.getBean(JobRunInputCleaner.class);
            assertTrue(AopUtils.isAopProxy(cleaner));

            try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                final var tasks = new ArrayList<Future<?>>();
                try {
                    for (var i = 0; i < 64; i++) {
                        tasks.add(executor.submit(() -> cleaner.cleanInputFiles(job, run)));
                    }
                    assertTrue(firstWave.await(5, TimeUnit.SECONDS));
                    assertFalse(overflow.await(250, TimeUnit.MILLISECONDS));
                } finally {
                    release.countDown();
                }
                for (final var task : tasks) {
                    task.get(5, TimeUnit.SECONDS);
                }
            }
        }
        assertEquals(limit, maximum.get());
        verify(storage, times(64)).deleteJobRunDirectory("job", "run");
    }
}
