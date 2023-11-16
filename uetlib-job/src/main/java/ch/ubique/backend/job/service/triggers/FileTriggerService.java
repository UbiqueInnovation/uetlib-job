package ch.ubique.backend.job.service.triggers;

import ch.ubique.backend.job.model.FileTriggerContext;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.triggers.FileChangeEvent;
import ch.ubique.backend.job.model.triggers.FileTrigger;

import jakarta.validation.constraints.NotNull;

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
import java.util.function.Consumer;

@Service
@Profile("!job-run")
public class FileTriggerService implements TriggerService<FileTrigger>, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(FileTriggerService.class);

    private final WatchService watchService;
    private final ExecutorService executorService;
    private final Map<Integer, RegisteredFileTrigger> registeredTriggers;
    private final Map<WatchKey, Path> watchedPaths;

    public FileTriggerService() throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.executorService = Executors.newSingleThreadExecutor();
        executorService.execute(this::watch);
        registeredTriggers = new ConcurrentHashMap<>();
        watchedPaths = new ConcurrentHashMap<>();
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
                    .filter(
                            triggerRunnablePair ->
                                    matches(triggerRunnablePair.fileTrigger(), path, event))
                    .forEach(
                            triggerRunnablePair -> {
                                final var jobRunArguments =
                                        new JobRunArguments(
                                                false, new FileTriggerContext(path, event), null);
                                triggerRunnablePair.consumer().accept(jobRunArguments);
                            });
        }
    }

    private boolean matches(final FileTrigger trigger, final Path path, final WatchEvent<?> event) {
        final var file = (Path) event.context();
        final var changeEvent = getChangeEvent(event.kind());

        return changeEvent == trigger.changeEvent()
                && path.equals(Path.of(trigger.folder()))
                && trigger.fileRegex().matcher(file.toString()).matches();
    }

    @Override
    public void addTrigger(
            final int triggerId,
            final FileTrigger triggerDefinition,
            final Consumer<JobRunArguments> consumer) {
        watchPath(Path.of(triggerDefinition.folder()));
        registeredTriggers.put(triggerId, new RegisteredFileTrigger(triggerDefinition, consumer));
    }

    private void watchPath(final Path path) {
        if (watchedPaths.containsValue(path)) {
            return;
        }

        if (!path.toFile().exists()) {
            final var dirsCreated = path.toFile().mkdirs();
            if (!dirsCreated) {
                throw new IllegalStateException(
                        "Could not create directories for watched path " + path);
            }
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
            final var successfulTermination = executorService.awaitTermination(5, TimeUnit.SECONDS);
            if (!successfulTermination) {
                logger.info(
                        "executorService failed to terminate successfully before timeout elapsed");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record RegisteredFileTrigger(
            @NotNull FileTrigger fileTrigger, @NotNull Consumer<JobRunArguments> consumer) {}
}
