package ch.uetlib.job.model.runner;

public enum JobApplicationExitStatus {
    SUCCESS,
    COULD_NOT_SCHEDULE,
    JOB_WARNING,
    JOB_ERROR,
    JOB_NOT_TERMINATED,
    JOB_NOT_FOUND,
    BAD_ARGUMENTS,
    UNCAUGHT_EXCEPTION;

    public int exitCode() {
        return ordinal();
    }
}
