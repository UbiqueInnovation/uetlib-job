package ch.ubique.backend.job.service.triggers;

import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.triggers.FileTrigger;

import jakarta.validation.constraints.NotNull;

import java.util.function.Consumer;

record RegisteredFileTrigger(
        @NotNull FileTrigger fileTrigger, @NotNull Consumer<JobRunArguments> consumer) {}
