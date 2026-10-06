package ch.ubique.backend.job.service.triggers.files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.den_4.inotify_java.Inotify;
import com.den_4.inotify_java.InotifyEvent;
import com.den_4.inotify_java.NativeInotify;
import com.den_4.inotify_java.enums.Event;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;

class InotifyFileWatcherTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void callbackFailureDoesNotEscapeListenerOrBlockLaterEvents(final boolean libraryLoaded)
            throws Exception {
        final var delivered = new ArrayList<FileWatcherEvent>();
        // Mock only the native resource boundary so listener behavior runs on every OS.
        try (final var nativeLibrary = mockStatic(NativeInotify.class);
                final var instances =
                        mockConstruction(
                                Inotify.class,
                                (inotify, context) ->
                                        when(inotify.addWatch("incoming", Event.All))
                                                .thenReturn(7))) {
            nativeLibrary.when(NativeInotify::isLibraryLoaded).thenReturn(libraryLoaded);
            try (final var watcher =
                    new InotifyFileWatcher(
                            event -> {
                                delivered.add(event);
                                if (event.filePath().equals(Path.of("first.txt")))
                                    throw new IllegalStateException("Callback failed");
                            })) {
                watcher.watchPath(Path.of("incoming"));
                assertDoesNotThrow(
                        () ->
                                watcher.filesystemEventOccurred(
                                        new InotifyEvent(7, Event.Create.value(), 0, "first.txt")));
                watcher.filesystemEventOccurred(
                        new InotifyEvent(7, Event.Create.value(), 0, "later.txt"));
                assertEquals(2, delivered.size());
                assertEquals(Path.of("later.txt"), delivered.get(1).filePath());
                assertEquals(Path.of("incoming"), delivered.get(1).watchedPath());
                nativeLibrary.verify(
                        () -> NativeInotify.loadLibrary(null), times(libraryLoaded ? 0 : 1));
            }
            verify(instances.constructed().getFirst()).destroy();
        }
    }
}
