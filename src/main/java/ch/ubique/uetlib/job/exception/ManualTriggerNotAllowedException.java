package ch.ubique.uetlib.job.exception;

public class ManualTriggerNotAllowedException extends Exception {
    public ManualTriggerNotAllowedException(final String msg) {
        super(msg);
    }
}
