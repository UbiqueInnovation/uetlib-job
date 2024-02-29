package ch.ubique.backend.job.service.triggers;

import ch.ubique.backend.job.model.FileTriggerContext;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.triggers.FileChangeEvent;
import ch.ubique.backend.job.model.triggers.FileTrigger;
import ch.ubique.backend.job.model.triggers.FileTriggerMatchResult;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;

public class FileTriggerWatcher {

    private static final Logger logger = LoggerFactory.getLogger(FileTriggerWatcher.class);

    private final WatchService watchService;
    private final Map<WatchKey, Path> watchedPaths;
    private final Map<Integer, RegisteredFileTrigger> registeredFileTriggers;

    public FileTriggerWatcher() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        watchedPaths = new ConcurrentHashMap<>();
        registeredFileTriggers = new ConcurrentHashMap<>();
    }

    void watch() {
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
            if (logger.isInfoEnabled()) {
                final var eventToString =
                        new ToStringBuilder(event, ToStringStyle.SHORT_PREFIX_STYLE)
                                .append("kind", event.kind())
                                .append("count", event.count())
                                .append("context", event.context())
                                .toString();
                logger.info("Got event: {}", eventToString);
            }
            registeredFileTriggers
                    .values()
                    .forEach(
                            registeredFileTrigger -> {
                                final var fileMatcher =
                                        fileMatcher(
                                                registeredFileTrigger.fileTrigger(),
                                                watchedPath,
                                                event);
                                if (fileMatcher == null || !fileMatcher.matches()) {
                                    // no match
                                    return;
                                }

                                final var fileTriggerMatchResult =
                                        FileTriggerMatchResult.from(fileMatcher);
                                final var fileTriggerContext =
                                        FileTriggerContext.from(
                                                watchedPath, event, fileTriggerMatchResult);
                                consumeJobRunArgument(registeredFileTrigger, fileTriggerContext);
                            });
        }
    }

    void consumeJobRunArgument(
            final RegisteredFileTrigger registeredFileTrigger,
            final FileTriggerContext fileTriggerContext) {
        final var jobRunArguments = new JobRunArguments(false, fileTriggerContext, null);
        registeredFileTrigger.consumer().accept(jobRunArguments);
    }

    private Matcher fileMatcher(
            final FileTrigger trigger, final Path watchedPath, final WatchEvent<?> event) {
        final var changeEvent = FileChangeEvent.fromWatchEventKind(event.kind());
        if (changeEvent != trigger.changeEvent()
                || !watchedPath.equals(Path.of(trigger.folder()))) {
            // event or watchPath does not match
            return null;
        }

        if (!(event.context() instanceof final Path eventContextPath)) {
            // not a path (can happen for eventKind OVERFLOW) so surely that does not match
            return null;
        }

        return trigger.fileRegex().matcher(eventContextPath.toString());
    }

    public void registerFileTrigger(
            final int triggerId,
            final FileTrigger triggerDefinition,
            final Consumer<JobRunArguments> consumer) {
        watchPath(Path.of(triggerDefinition.folder()));
        registeredFileTriggers.put(
                triggerId, new RegisteredFileTrigger(triggerDefinition, consumer));
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

    public void removeFileTrigger(final int triggerId) {
        registeredFileTriggers.remove(triggerId);
    }
}
