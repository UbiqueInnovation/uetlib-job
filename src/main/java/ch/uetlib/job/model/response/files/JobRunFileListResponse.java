package ch.uetlib.job.model.response.files;

import jakarta.validation.constraints.NotBlank;

import java.util.Collection;

public record JobRunFileListResponse(Collection<@NotBlank String> paths) {}
