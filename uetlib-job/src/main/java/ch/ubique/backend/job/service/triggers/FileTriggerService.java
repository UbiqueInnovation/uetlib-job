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
        final var watchedPath = watchedPaths.get(key);
        logger.info("Watched path: {}", watchedPath);

        for (final var event : key.pollEvents()) {
            logger.info("Got eventContext {}", event);
            registeredTriggers.values().stream()
                    .filter(
                            triggerRunnablePair ->
                                    matches(triggerRunnablePair.fileTrigger(), watchedPath, event))
                    .forEach(
                            triggerRunnablePair -> {
                                final var fileTriggerContext =
                                        FileTriggerContext.from(watchedPath, event);
                                final var jobRunArguments =
                                        new JobRunArguments(false, fileTriggerContext, null);
                                triggerRunnablePair.consumer().accept(jobRunArguments);
                            });
        }
    }

    private boolean matches(
            final FileTrigger trigger, final Path watchedPath, final WatchEvent<?> event) {
        final var changeEvent = FileChangeEvent.fromWatchEventKind(event.kind());
        if (changeEvent != trigger.changeEvent()
                || !watchedPath.equals(Path.of(trigger.folder()))) {
            // event or watchPath does not match
            return false;
        }

        if (!(event.context() instanceof final Path eventContextPath)) {
            // not a path (can happen for eventKind OVERFLOW) so surely that does not match
            return false;
        }

        return trigger.fileRegex().matcher(eventContextPath.toString()).matches();
    }

    private boolean matchesAssumeEvent(
            final FileTrigger trigger, final Path watchedPath, final Object eventContext) {
        if (!(eventContext instanceof final Path eventContextPath)) {
            // not a path (can happen for eventKind OVERFLOW) so surely that does not match
            return false;
        }

        return trigger.fileRegex().matcher(eventContextPath.toString()).matches();
    }

    @Override
    public void addTrigger(
            final int triggerId,
            final FileTrigger triggerDefinition,
            final Consumer<JobRunArguments> consumer) {
        watchPath(Path.of(triggerDefinition.folder()));
        registeredTriggers.put(triggerId, new RegisteredFileTrigger(triggerDefinition, consumer));
    }

    private void watchPath(final Path pathToWatch) {
        if (watchedPaths.containsValue(pathToWatch)) {
            return;
        }

        if (!pathToWatch.toFile().exists()) {
            final var dirsCreated = pathToWatch.toFile().mkdirs();
            if (!dirsCreated) {
                throw new IllegalStateException(
                        "Could not create directories for watched path " + pathToWatch);
            }
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
