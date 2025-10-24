package ch.ubique.backend.job.service.triggers;

import ch.ubique.backend.job.model.FileTriggerContext;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.triggers.FileTrigger;
import ch.ubique.backend.job.model.triggers.TriggerType;
import ch.ubique.backend.job.service.triggers.files.FileWatcher;
import ch.ubique.backend.job.service.triggers.files.FileWatcherEvent;
import ch.ubique.backend.job.service.triggers.files.InotifyFileWatcher;
import ch.ubique.backend.job.service.triggers.files.JavaNioFileWatcher;
import ch.ubique.backend.job.service.triggers.files.RegisteredFileTrigger;

import com.den_4.inotify_java.exceptions.InotifyException;

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
        registeredFileTriggers = new ConcurrentHashMap<>();
        fileTriggerWatcher = createFileWatcher();
    }

    private FileWatcher createFileWatcher() throws IOException {
        try {
            return new InotifyFileWatcher(this::onEvent);
        } catch (final UnsatisfiedLinkError | InotifyException e) {
            logger.info("Inotify not available, falling back to Java NIO FileWatcher.");
            return new JavaNioFileWatcher(this::onEvent);
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
        registeredFileTriggers
                .values()
                .forEach(registeredFileTrigger -> onEvent(event, registeredFileTrigger));
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
