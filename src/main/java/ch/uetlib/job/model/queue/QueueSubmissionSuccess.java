package ch.uetlib.job.model.queue;

import ch.uetlib.job.model.JobQueuedRun;

public record QueueSubmissionSuccess(JobQueuedRun queuedRun) implements QueueSubmissionResult {}
