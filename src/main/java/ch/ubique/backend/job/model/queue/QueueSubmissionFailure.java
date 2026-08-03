package ch.ubique.backend.job.model.queue;

public record QueueSubmissionFailure(String reason) implements QueueSubmissionResult {}
