package ch.ubique.backend.job.model.executer;

public enum JobSubmissionStatus {
    /** Successfully submitted job */
    SUBMITTED(true),
    /** Submitting the job was disallowed. E.g., due to parallel runs not being allowed. */
    DISALLOWED(false),
    /** Was allowed to submit, but failed. */
    FAILED(false);

    private final boolean isSuccess;

    JobSubmissionStatus(final boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    public boolean isSuccess() {
        return isSuccess;
    }
}
