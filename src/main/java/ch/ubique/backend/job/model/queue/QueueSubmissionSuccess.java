package ch.ubique.backend.job.model.queue;

import ch.ubique.backend.job.model.JobQueuedRun;

public record QueueSubmissionSuccess(JobQueuedRun queuedRun) implements QueueSubmissionResult {}
