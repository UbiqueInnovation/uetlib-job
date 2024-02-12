package ch.ubique.backend.job.exception;

public class ManualTriggerNotAllowedException extends Exception {
    public ManualTriggerNotAllowedException(final String msg) {
        super(msg);
    }
}
