package ch.ubique.backend.job.model.triggers;

import java.util.Arrays;

public enum TriggerType {
    CRON(CronTrigger.class),
    STARTUP(RunOnStartupTrigger.class),
    FIXED_RATE(FixedRateTrigger.class),
    JOB_RUN_STATE(JobStateTrigger.class);

    private final Class<? extends TriggerDefinition> defintionClass;

    TriggerType(final Class<? extends TriggerDefinition> defintionClass) {
        this.defintionClass = defintionClass;
    }

    public Class<? extends TriggerDefinition> defintionClass() {
        return defintionClass;
    }

    public static TriggerType getTypeForClass(
            final Class<? extends TriggerDefinition> defintionClass) {
        return Arrays.stream(TriggerType.values())
                .filter(type -> type.defintionClass.equals(defintionClass))
                .findFirst()
                .orElseThrow();
    }
}
