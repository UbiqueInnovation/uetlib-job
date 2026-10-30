package ch.ubique.uetlib.job.service.triggers.files;

import ch.ubique.uetlib.job.model.triggers.FileChangeEvent;
import java.nio.file.Path;

public record FileWatcherEvent(Path watchedPath, Path filePath, FileChangeEvent type) {

    public FileWatcherEvent withType(final FileChangeEvent type) {
        return new FileWatcherEvent(watchedPath, filePath, type);
    }
}
