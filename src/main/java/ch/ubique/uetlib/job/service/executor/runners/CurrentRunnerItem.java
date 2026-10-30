package ch.ubique.uetlib.job.service.executor.runners;

import ch.ubique.uetlib.job.Job;

public record CurrentRunnerItem(Class<? extends Job> jobClass, int jobRunId) {}
