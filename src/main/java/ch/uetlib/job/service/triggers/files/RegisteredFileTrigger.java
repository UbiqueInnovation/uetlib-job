package ch.uetlib.job.service.triggers.files;

import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.triggers.FileTrigger;

import java.util.function.Consumer;

public record RegisteredFileTrigger(FileTrigger fileTrigger, Consumer<JobRunArguments> consumer) {}
