package ch.ubique.backend.job.triggers;

public sealed interface Trigger permits CronTrigger, JobStateTrigger, StartupTrigger {
    String type();
}
