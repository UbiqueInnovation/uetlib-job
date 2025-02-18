package ch.ubique.backend.job.model.executer;

public record UnsuccessfulSubmissionResult(JobSubmissionStatus status)
        implements JobSubmissionResult {

    public UnsuccessfulSubmissionResult {
        if (status.isSuccess()) {
            throw new IllegalArgumentException("Only an unsuccessful status is allowed.");
        }
    }
}
