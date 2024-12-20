package ch.ubique.backend.job.model.response.files;

import jakarta.validation.constraints.NotBlank;
import java.util.Collection;

public record JobRunFileListResponse(@NotBlank Collection<String> paths) {}
