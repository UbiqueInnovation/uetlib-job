package ch.ubique.uetlib.job.service.triggers.files;

import ch.ubique.uetlib.job.model.triggers.FileChangeEvent;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.exception.UncheckedException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JavaNioFileWatcher implements AutoCloseable, FileWatcher {

    private static final Logger logger = LoggerFactory.getLogger(JavaNioFileWatcher.class);

    private final WatchService watchService;
    private final Map<WatchKey, Path> watchedPaths;

    private final Consumer<FileWatcherEvent> onEvent;

    private final ExecutorService fileTriggerWatcherExecutorService;

    public JavaNioFileWatcher(final Consumer<FileWatcherEvent> onEvent) throws IOException {
        this(onEvent, FileSystems.getDefault().newWatchService());
    }

    JavaNioFileWatcher(final Consumer<FileWatcherEvent> onEvent, final WatchService watchService) {
        this.onEvent = onEvent;
        this.watchService = watchService;
        this.watchedPaths = new ConcurrentHashMap<>();

        final var threadFactory =
                new ThreadFactoryBuilder().setNameFormat("file-trigger-service").build();
        this.fileTriggerWatcherExecutorService = Executors.newSingleThreadExecutor(threadFactory);
        fileTriggerWatcherExecutorService.execute(this::watch);
    }

    public void watch() {
        logger.debug("Starting to watch for file events.");

        while (!Thread.currentThread().isInterrupted()) {
            final var key = waitForNextWatchKey();
            if (key == null) {
                return;
            }

            try {
                checkForMatchingTriggerDefinitions(key);
            } finally {
                // A key must be reset after processing, including failed callbacks, or this
                // directory will stop reporting new events.
                if (!key.reset()) {
                    final var path = watchedPaths.remove(key);
                    logger.warn("The watch key for {} is not valid anymore.", path);
                }
            }
        }
    }

    @Nullable
    private WatchKey waitForNextWatchKey() {
        try {
            return watchService.take();
        } catch (final ClosedWatchServiceException _) {
            return null;
        } catch (final InterruptedException e) {
            if (!fileTriggerWatcherExecutorService.isShutdown()) {
                logger.warn("File watcher was interrupted", e);
            }
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void checkForMatchingTriggerDefinitions(final WatchKey key) {
        final var watchedPath = watchedPaths.get(key);
        if (watchedPath == null) {
            return;
        }
        logger.debug("Watched path: {}", watchedPath);

        for (final var event : key.pollEvents()) {
            if (logger.isDebugEnabled()) {
                final var eventToString =
                        new ToStringBuilder(event, ToStringStyle.SHORT_PREFIX_STYLE)
                                .append("kind", event.kind())
                                .append("count", event.count())
                                .append("context", event.context())
                                .toString();
                logger.debug("Got event: {}", eventToString);
            }
            if (!(event.context() instanceof final Path eventContextPath)) {
                // OVERFLOW has no path. Do not discard other events in the same batch.
                logger.warn(
                        "File watch event without a path for {}: {}", watchedPath, event.kind());
                continue;
            }
            try {
                final var type = FileChangeEvent.fromWatchEventKind(event.kind());
                final var watcherEvent = new FileWatcherEvent(watchedPath, eventContextPath, type);
                onEvent.accept(watcherEvent);
                if (type == FileChangeEvent.ENTRY_CREATED) {
                    onEvent.accept(watcherEvent.withType(FileChangeEvent.ENTRY_WRITE_CLOSE));
                    onEvent.accept(watcherEvent.withType(FileChangeEvent.ENTRY_MOVED_TO));
                }
            } catch (final Exception e) {
                // The executor starts this watch loop only once; it does not restart the task
                // if event conversion or a callback throws. Skip this failed event and keep
                // processing later files. FileTriggerService isolates individual job failures.
                logger.error(
                        "Failed to process file watch event for {}: {}", watchedPath, event, e);
            }
        }
    }

    @Override
    public void watchPath(final Path pathToWatch) {
        if (watchedPaths.containsValue(pathToWatch)) {
            return;
        }

        try {
            final var watchKey =
                    pathToWatch.register(
                            watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE);

            watchedPaths.put(watchKey, pathToWatch);
        } catch (final IOException e) {
            throw new UncheckedException(e);
        }
    }

    @Override
    public void close() {
        logger.debug("Shutting down file trigger watcher");
        // Closing also releases native watch resources and unblocks take() immediately.
        try {
            watchService.close();
        } catch (final IOException e) {
            logger.warn("Failed to close file watch service", e);
        } finally {
            fileTriggerWatcherExecutorService.shutdownNow();
        }
        try {
            final var successfulTermination =
                    fileTriggerWatcherExecutorService.awaitTermination(5, TimeUnit.SECONDS);
            if (!successfulTermination) {
                logger.debug(
                        "Failed to shutdown file trigger watcher successfully before timeout elapsed, will force shutting down executorService");
                fileTriggerWatcherExecutorService
                        .shutdownNow(); // Forcibly interrupts threads if needed
            }
        } catch (final InterruptedException e) {
            logger.debug(
                    "Interrupted while awaiting termination of file trigger watcher, will force shutting down executorService");
            fileTriggerWatcherExecutorService.shutdownNow(); // Ensure threads are forcibly stopped
            Thread.currentThread().interrupt();
        }
    }
}
