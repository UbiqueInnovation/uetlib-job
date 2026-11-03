package ch.ubique.uetlib.job.service.triggers.files;

import ch.ubique.uetlib.job.model.JobRunArguments;
import ch.ubique.uetlib.job.model.triggers.FileTrigger;

import java.util.function.Consumer;

public record RegisteredFileTrigger(FileTrigger fileTrigger, Consumer<JobRunArguments> consumer) {}
