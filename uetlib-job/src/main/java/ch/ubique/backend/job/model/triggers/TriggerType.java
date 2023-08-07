package ch.ubique.backend.job.model.triggers;

public enum TriggerType {
    CRON,
    STARTUP,
    JOB_RUN_STATE;

    public static Class<? extends Trigger> getClassForType(final TriggerType type) {
        return switch (type) {
            case CRON -> CronTrigger.class;
            case STARTUP -> StartupTrigger.class;
            case JOB_RUN_STATE -> JobStateTrigger.class;
        };
    }
}
