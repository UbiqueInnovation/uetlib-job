package ch.ubique.backend.job.model.queue;

public sealed interface QueueSubmissionResult permits QueueSubmissionSuccess, QueueSubmissionFailure {}
