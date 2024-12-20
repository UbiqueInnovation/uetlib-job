package ch.ubique.backend.job.files;

import ch.ubique.backend.core.io.InputStreamProvider;

public record JobFile(String path, InputStreamProvider inputStreamProvider) {}
