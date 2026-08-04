package ch.uetlib.job.model.queue;

public sealed interface QueueSubmissionResult
        permits QueueSubmissionSuccess, QueueSubmissionFailure {}
