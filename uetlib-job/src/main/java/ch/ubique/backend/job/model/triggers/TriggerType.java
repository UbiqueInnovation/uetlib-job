package ch.ubique.backend.job.model.triggers;

public enum TriggerType {
    CRON,
    STARTUP,
    JOB_RUN_STATE;

    public static Class<? extends TriggerDefinition> getClassForType(final TriggerType type) {
        return switch (type) {
            case CRON -> CronTriggerDefinition.class;
            case STARTUP -> StartupTriggerDefinition.class;
            case JOB_RUN_STATE -> JobStateTriggerDefinition.class;
        };
    }
}
