package ch.uetlib.job.service.triggers;

import ch.uetlib.inotify.exceptions.InotifyException;
import ch.uetlib.job.model.FileTriggerContext;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.triggers.FileTrigger;
import ch.uetlib.job.model.triggers.TriggerType;
import ch.uetlib.job.service.triggers.files.FileWatcher;
import ch.uetlib.job.service.triggers.files.FileWatcherEvent;
import ch.uetlib.job.service.triggers.files.InotifyFileWatcher;
import ch.uetlib.job.service.triggers.files.JavaNioFileWatcher;
import ch.uetlib.job.service.triggers.files.RegisteredFileTrigger;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;

@Service
@Profile("!job-run")
public class FileTriggerService implements TriggerService<FileTrigger>, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(FileTriggerService.class);

    private final FileWatcher fileTriggerWatcher;
    private final Map<Integer, RegisteredFileTrigger> registeredFileTriggers;

    public FileTriggerService() throws IOException {
        this(FileTriggerService::createFileWatcher);
    }

    FileTriggerService(final FileWatcherFactory factory) throws IOException {
        registeredFileTriggers = new ConcurrentHashMap<>();
        fileTriggerWatcher = factory.create(this::onEvent);
    }

    @FunctionalInterface
    interface FileWatcherFactory {
        FileWatcher create(Consumer<FileWatcherEvent> onEvent) throws IOException;
    }

    private static FileWatcher createFileWatcher(final Consumer<FileWatcherEvent> onEvent)
            throws IOException {
        try {
            return new InotifyFileWatcher(onEvent);
        } catch (final UnsatisfiedLinkError | InotifyException e) {
            logger.info("Inotify not available, falling back to Java NIO FileWatcher.");
            return new JavaNioFileWatcher(onEvent);
        }
    }

    @Override
    public void addTrigger(
            final int triggerId,
            final FileTrigger triggerDefinition,
            final JobClassAndId job,
            final Consumer<JobRunArguments> consumer) {
        registerFileTrigger(triggerId, triggerDefinition, consumer);
    }

    @Override
    public void removeTrigger(final int triggerId) {
        registeredFileTriggers.remove(triggerId);
    }

    @Override
    public void close() throws Exception {
        fileTriggerWatcher.close();
    }

    public void registerFileTrigger(
            final int triggerId,
            final FileTrigger triggerDefinition,
            final Consumer<JobRunArguments> consumer) {
        final var pathToWatch = Path.of(triggerDefinition.folder());
        ensureDirectoryExists(pathToWatch);
        fileTriggerWatcher.watchPath(pathToWatch);
        registeredFileTriggers.put(
                triggerId, new RegisteredFileTrigger(triggerDefinition, consumer));
    }

    private void onEvent(final FileWatcherEvent event) {
        logger.debug("Got {}", event);
        registeredFileTriggers.forEach((triggerId, trigger) -> {
            try {
                onEvent(event, trigger);
            } catch (final Exception e) {
                // Job submission accesses the database and can fail during an outage.
                // Letting that exception escape kills the watcher, so later files would
                // require an application restart. Log this failed event and continue with
                // other triggers and future files; this does not retry the failed event.
                logger.error(
                        "Failed to process file event {} for trigger {} ({}).",
                        event,
                        triggerId,
                        trigger.fileTrigger(),
                        e);
            }
        });
    }

    private void onEvent(final FileWatcherEvent event, final RegisteredFileTrigger trigger) {
        final var fileMatcher = fileMatcher(trigger, event);
        if (fileMatcher == null) {
            return;
        }
        final var fileTriggerContext = FileTriggerContext.from(event, fileMatcher);
        final var jobRunArguments =
                new JobRunArguments(false, TriggerType.FILE, fileTriggerContext, null, null);
        logger.debug("Calling consumer for {}", trigger.fileTrigger());
        trigger.consumer().accept(jobRunArguments);
    }

    @Nullable
    private Matcher fileMatcher(final RegisteredFileTrigger trigger, final FileWatcherEvent event) {
        final var fileTrigger = trigger.fileTrigger();
        if (event.type() != fileTrigger.changeEvent()
                || !event.watchedPath().equals(Path.of(fileTrigger.folder()))) {
            return null; // event type or watchPath does not match
        }
        final var matcher = fileTrigger.fileRegex().matcher(event.filePath().toString());
        if (!matcher.matches()) {
            return null; // file regex does not match
        }
        return matcher;
    }

    private static void ensureDirectoryExists(final Path path) {
        if (!path.toFile().exists()) {
            final var dirsCreated = path.toFile().mkdirs();
            if (!dirsCreated) {
                throw new IllegalStateException(
                        "Could not create directories for watched path " + path);
            }
        } else if (!path.toFile().isDirectory()) {
            throw new IllegalStateException("Path must be a directory.");
        }
    }
}
