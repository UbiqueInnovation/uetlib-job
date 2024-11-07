package ch.ubique.backend.job.model.client.info;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record JobSystemInfoResponse(@NotNull @Valid JobSystemInfo info) {}
