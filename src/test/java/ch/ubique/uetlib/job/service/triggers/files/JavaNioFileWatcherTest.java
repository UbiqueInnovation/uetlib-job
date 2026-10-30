package ch.ubique.uetlib.job.service.triggers.files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.ubique.uetlib.job.model.triggers.FileChangeEvent;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaNioFileWatcherTest {
    @TempDir Path directory;

    @Test
    void callbackFailureDoesNotStopLaterFiles() throws Exception {
        final var failures = new LinkedBlockingQueue<Path>();
        final var delivered = new LinkedBlockingQueue<Path>();
        final var firstFile = Path.of("first.txt");
        final var laterFile = Path.of("later.txt");
        try (final var watcher =
                new JavaNioFileWatcher(
                        event -> {
                            if (event.type() != FileChangeEvent.ENTRY_CREATED) return;
                            if (event.filePath().equals(firstFile)) {
                                failures.add(event.filePath());
                                throw new IllegalStateException("Callback failed");
                            }
                            delivered.add(event.filePath());
                        })) {
            watcher.watchPath(directory);
            Files.createFile(directory.resolve(firstFile));
            assertEquals(firstFile, failures.poll(10, TimeUnit.SECONDS));
            Files.createFile(directory.resolve(laterFile));
            assertEquals(laterFile, delivered.poll(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void closingIdleWatcherReleasesResourcesAndTerminatesPromptly() throws Exception {
        try (final var watcher = new JavaNioFileWatcher(_ -> fail("Unexpected event"))) {
            watcher.watchPath(directory);
            assertTimeoutPreemptively(Duration.ofSeconds(2), watcher::close);
            // A new path forces registration against the closed native watch service.
            final var anotherDirectory = Files.createDirectory(directory.resolve("another"));
            assertThrows(
                    ClosedWatchServiceException.class, () -> watcher.watchPath(anotherDirectory));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void overflowDoesNotDiscardLaterEventAndInvalidKeyDoesNotStopOtherDirectories()
            throws Exception {
        final var watchService = mock(WatchService.class);
        final var invalidPath = mock(Path.class);
        final var validPath = mock(Path.class);
        final var invalidKey = mock(WatchKey.class);
        final var validKey = mock(WatchKey.class);
        final WatchEvent<?> overflow = mock(WatchEvent.class);
        final WatchEvent<Path> created = mock(WatchEvent.class);
        final var ready = new CountDownLatch(1);
        final var delivered = new LinkedBlockingQueue<FileWatcherEvent>();
        final var laterFile = Path.of("later.txt");
        when(invalidPath.register(eq(watchService), any(WatchEvent.Kind[].class)))
                .thenReturn(invalidKey);
        when(validPath.register(eq(watchService), any(WatchEvent.Kind[].class)))
                .thenReturn(validKey);
        when(invalidKey.pollEvents()).thenReturn(List.of(overflow));
        when(invalidKey.reset()).thenReturn(false);
        when(validKey.pollEvents()).thenReturn(List.of(overflow, created));
        when(validKey.reset()).thenReturn(true);
        doReturn(StandardWatchEventKinds.OVERFLOW).when(overflow).kind();
        when(created.kind()).thenReturn(StandardWatchEventKinds.ENTRY_CREATE);
        when(created.context()).thenReturn(laterFile);
        final var keys = new AtomicInteger();
        when(watchService.take())
                .thenAnswer(
                        _ -> {
                            assertTrue(ready.await(10, TimeUnit.SECONDS));
                            return switch (keys.getAndIncrement()) {
                                case 0 -> invalidKey;
                                case 1 -> validKey;
                                default -> throw new ClosedWatchServiceException();
                            };
                        });
        try (final var watcher = new JavaNioFileWatcher(delivered::add, watchService)) {
            watcher.watchPath(invalidPath);
            watcher.watchPath(validPath);
            ready.countDown();
            final var event = delivered.poll(10, TimeUnit.SECONDS);
            assertNotNull(event);
            assertEquals(validPath, event.watchedPath());
            assertEquals(laterFile, event.filePath());
            assertEquals(FileChangeEvent.ENTRY_CREATED, event.type());
            verify(invalidKey, timeout(1000)).reset();
            verify(validKey, timeout(1000)).reset();
        }
        verify(watchService).close();
    }
}
