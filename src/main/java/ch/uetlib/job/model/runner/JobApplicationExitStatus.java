package ch.uetlib.job.model.runner;

public enum JobApplicationExitStatus {
    SUCCESS(0),
    COULD_NOT_SCHEDULE(1),
    JOB_WARNING(2),
    JOB_ERROR(3),
    JOB_NOT_TERMINATED(4),
    JOB_NOT_FOUND(5),
    BAD_ARGUMENTS(6),
    UNCAUGHT_EXCEPTION(7);

    private final int exitCode;

    JobApplicationExitStatus(final int exitCode) {
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }
}
