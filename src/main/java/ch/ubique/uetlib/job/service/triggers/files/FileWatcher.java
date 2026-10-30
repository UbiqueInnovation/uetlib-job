package ch.ubique.uetlib.job.service.triggers.files;

import java.nio.file.Path;

public sealed interface FileWatcher extends AutoCloseable
        permits JavaNioFileWatcher, InotifyFileWatcher {

    void watchPath(Path of);
}
