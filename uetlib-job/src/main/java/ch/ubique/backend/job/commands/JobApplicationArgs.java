package ch.ubique.backend.job.commands;

import jakarta.annotation.Nullable;

public record JobApplicationArgs(@Nullable JobApplicationJobToStartArgs jobToStart) {}
