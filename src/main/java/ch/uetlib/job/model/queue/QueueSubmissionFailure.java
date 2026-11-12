package ch.uetlib.job.model.queue;

public record QueueSubmissionFailure(String reason) implements QueueSubmissionResult {}
