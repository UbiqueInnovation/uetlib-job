package ch.ubique.backend.job.triggers;

public record CronTrigger(String cronExpression) implements Trigger {

    @Override
    public String type() {
        return "cron";
    }
}
