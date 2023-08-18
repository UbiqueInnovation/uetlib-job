package ch.ubique.backend.job.service.triggers;

import ch.ubique.backend.job.model.triggers.FileChangeEvent;
import ch.ubique.backend.job.model.triggers.FileTrigger;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Profile("!job-run")
public class FileTriggerService implements TriggerService<FileTrigger>, AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final WatchService watchService;
    private final ExecutorService executorService;
    private final Map<Integer, Pair<FileTrigger, Runnable>> registeredTriggers;
    private final Map<WatchKey, Path> watchedPaths;

    public FileTriggerService() throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.executorService = Executors.newSingleThreadExecutor();
        executorService.execute(this::watch);
        registeredTriggers = new ConcurrentHashMap<>();
        watchedPaths = new ConcurrentHashMap<>();
    }

    private void watch() {
        logger.info("Starting to watch for file events.");

        while (true) {
            final var key = waitForNextWatchKey();
            if (key == null) {
                return;
            }

            checkForMatchingTriggerDefinitions(key);

            final var valid = key.reset();
            if (!valid) {
                logger.warn("The watch key {} is not valid anymore.", key);
                break;
            }
        }
    }

    private WatchKey waitForNextWatchKey() {
        try {
            return watchService.take();
        } catch (final InterruptedException e) {
            logger.warn("File watcher was interrupted", e);
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void checkForMatchingTriggerDefinitions(final WatchKey key) {
        final var path = watchedPaths.get(key);
        logger.info("Watched path: {}", path);

        for (final var event : key.pollEvents()) {
            logger.info("Got event {}", event);
            registeredTriggers.values().stream()
                    .filter(trigger -> matches(trigger.getKey(), path, event))
                    .forEach(trigger -> trigger.getValue().run());
        }
    }

    private boolean matches(final FileTrigger trigger, final Path path, final WatchEvent<?> event) {
        final var file = (Path) event.context();
        final var changeEvent = getChangeEvent(event.kind());

        return changeEvent == trigger.changeEvent()
                && path.equals(Path.of(trigger.folder()))
                && file.toString().matches(trigger.fileRegex());
    }

    private static FileChangeEvent getChangeEvent(final WatchEvent.Kind<?> kind) {
        if (kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
            return FileChangeEvent.ENTRY_CREATED;
        }
        if (kind.equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
            return FileChangeEvent.ENTRY_MODIFIED;
        }
        if (kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
            return FileChangeEvent.ENTRY_DELETED;
        }
        if (kind.equals(StandardWatchEventKinds.OVERFLOW)) {
            return FileChangeEvent.OVERFLOW;
        }
        throw new IllegalArgumentException("Got unknown event kind " + kind);
    }

    @Override
    public void addTrigger(
            final int triggerId, final FileTrigger triggerDefinition, final Runnable runnable) {
        watchPath(Path.of(triggerDefinition.folder()));
        registeredTriggers.put(triggerId, new ImmutablePair<>(triggerDefinition, runnable));
    }

    private void watchPath(final Path path) {
        if (watchedPaths.containsValue(path)) {
            return;
        }

        try {
            final var key =
                    path.register(
                            watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE);

            watchedPaths.put(key, path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void removeTrigger(final int triggerId) {
        registeredTriggers.remove(triggerId);
    }

    @Override
    public void close() {
        executorService.shutdown();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
