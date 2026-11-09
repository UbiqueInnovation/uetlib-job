package ch.uetlib.job.commands;

import org.jspecify.annotations.Nullable;

public record JobApplicationArgs(@Nullable JobApplicationJobToStartArgs jobToStart) {}
