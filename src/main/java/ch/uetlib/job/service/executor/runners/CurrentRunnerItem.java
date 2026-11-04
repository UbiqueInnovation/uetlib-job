package ch.uetlib.job.service.executor.runners;

import ch.uetlib.job.Job;

public record CurrentRunnerItem(Class<? extends Job> jobClass, int jobRunId) {}
