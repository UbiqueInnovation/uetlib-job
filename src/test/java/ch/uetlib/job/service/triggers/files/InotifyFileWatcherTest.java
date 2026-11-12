package ch.uetlib.job.service.triggers.files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.uetlib.inotify.Inotify;
import ch.uetlib.inotify.InotifyEvent;
import ch.uetlib.inotify.NativeInotify;
import ch.uetlib.inotify.enums.Event;

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
        final var incoming = Path.of("incoming");
        // Mock only the native resource boundary so listener behavior runs on every OS.
        try (final var nativeLibrary = mockStatic(NativeInotify.class);
                final var instances =
                        mockConstruction(
                                Inotify.class,
                                (inotify, _) ->
                                        when(inotify.addWatch(incoming.toString(), Event.All))
                                                .thenReturn(7))) {
            nativeLibrary.when(NativeInotify::isLibraryLoaded).thenReturn(libraryLoaded);
            try (final var watcher =
                    new InotifyFileWatcher(
                            event -> {
                                delivered.add(event);
                                if (event.filePath().equals(Path.of("first.txt"))) {
                                    throw new IllegalStateException("Callback failed");
                                }
                            })) {
                watcher.watchPath(incoming);
                assertDoesNotThrow(
                        () ->
                                watcher.filesystemEventOccurred(
                                        new InotifyEvent(7, Event.Create.value(), 0, "first.txt")));
                watcher.filesystemEventOccurred(
                        new InotifyEvent(7, Event.Create.value(), 0, "later.txt"));
                assertEquals(2, delivered.size());
                assertEquals(Path.of("later.txt"), delivered.get(1).filePath());
                assertEquals(incoming, delivered.get(1).watchedPath());
                nativeLibrary.verify(
                        () -> NativeInotify.loadLibrary(null), times(libraryLoaded ? 0 : 1));
            }
            verify(instances.constructed().getFirst()).close();
        }
    }
}
