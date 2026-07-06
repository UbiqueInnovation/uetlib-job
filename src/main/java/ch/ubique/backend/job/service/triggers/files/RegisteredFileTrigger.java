package ch.ubique.backend.job.service.triggers.files;

import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.triggers.FileTrigger;

import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

public record RegisteredFileTrigger(
         FileTrigger fileTrigger,  Consumer<JobRunArguments> consumer) {}
