package ch.ubique.backend.job.triggers;

public record StartupTrigger() implements Trigger {

    @Override
    public String type() {
        return "startup";
    }
}
