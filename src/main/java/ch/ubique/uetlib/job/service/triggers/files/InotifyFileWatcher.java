package ch.ubique.uetlib.job.service.triggers.files;

import ch.ubique.uetlib.job.model.triggers.FileChangeEvent;

import com.den_4.inotify_java.EventQueueFull;
import com.den_4.inotify_java.Inotify;
import com.den_4.inotify_java.InotifyEvent;
import com.den_4.inotify_java.InotifyEventListener;
import com.den_4.inotify_java.NativeInotify;
import com.den_4.inotify_java.enums.Event;
import com.den_4.inotify_java.exceptions.InotifyException;

import org.apache.commons.lang3.exception.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class InotifyFileWatcher implements FileWatcher, InotifyEventListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Inotify inotify;
    private final Consumer<FileWatcherEvent> onEvent;
    private final Map<Integer, Path> pathsByWatchDescriptor;

    public InotifyFileWatcher(final Consumer<FileWatcherEvent> onEvent) throws InotifyException {
        this.onEvent = onEvent;
        this.pathsByWatchDescriptor = new ConcurrentHashMap<>();
        if (!NativeInotify.isLibraryLoaded()) {
            NativeInotify.loadLibrary(null);
        }
        this.inotify = new Inotify();
        logger.info("Initialized InotifyFileWatcher.");
    }

    @Override
    public void watchPath(final Path path) {
        if (pathsByWatchDescriptor.containsValue(path)) {
            logger.debug("Path {} already watched.", path);
            return;
        }
        try {
            final var watchDescriptor = inotify.addWatch(path.toString(), Event.All);
            inotify.addListener(watchDescriptor, this);
            pathsByWatchDescriptor.put(watchDescriptor, path);
            logger.debug("Added listener for {}, watchDescriptor={}.", path, watchDescriptor);
        } catch (final InotifyException e) {
            throw new UncheckedException(e);
        }
    }

    @Override
    public void filesystemEventOccurred(final InotifyEvent event) {
        try {
            handleEvent(event);
        } catch (final Exception e) {
            // An exception escaping this listener makes ConcurrentReader destroy the inotify
            // instance. Keep decoding and callback failures inside the listener boundary.
            logger.error("Failed to process inotify event {}", event, e);
        }
    }

    private void handleEvent(final InotifyEvent event) {
        logger.debug("Got {}", event);
        final var watchDescriptor = event.getSource();
        final var watchedPath = pathsByWatchDescriptor.get(watchDescriptor);
        if (watchedPath == null) {
            logger.debug("No watched path for descriptor {}", watchDescriptor);
            return;
        }
        final var filePath = event.getName();
        final var eventType = FileChangeEvent.fromInotifyEvent(event);
        if (eventType == FileChangeEvent.UNKNOWN) {
            logger.debug("Event {} mapped to {}.", event, eventType);
            return;
        }
        if (filePath == null) {
            logger.debug("No file associated with event {}", event);
            return;
        }
        onEvent.accept(new FileWatcherEvent(watchedPath, Path.of(filePath), eventType));
    }

    @Override
    public void queueFull(final EventQueueFull eventQueueFull) {
        logger.warn("Inotify queue full: {}", eventQueueFull);
    }

    @Override
    public void close() {
        inotify.destroy();
    }
}
