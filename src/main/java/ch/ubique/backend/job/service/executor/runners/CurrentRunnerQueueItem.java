package ch.ubique.backend.job.service.executor.runners;

import ch.ubique.backend.job.Job;

public record CurrentRunnerQueueItem(Class<? extends Job> jobClass, int jobRunId) {}
