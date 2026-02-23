package ch.ubique.backend.job.model.triggers;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum TriggerType {
    CRON(CronTrigger.class),
    STARTUP(RunOnStartupTrigger.class),
    FIXED_RATE(FixedRateTrigger.class),
    FILE(FileTrigger.class),
    JOB_RUN_STATE(JobRunStateTrigger.class),
    RETRY(RetryTrigger.class),
    DATABASE_SUBMISSION(DatabaseSubmissionTrigger.class),
    PG_LISTEN(PgListenTrigger.class);

    private static final Map<Class<? extends TriggerDefinition>, TriggerType>
            TRIGGER_TYPE_BY_DEFINITION_CLASS =
                    Arrays.stream(TriggerType.values())
                            .collect(
                                    Collectors.toUnmodifiableMap(
                                            TriggerType::defintionClass, Function.identity()));

    private final Class<? extends TriggerDefinition> defintionClass;

    TriggerType(final Class<? extends TriggerDefinition> defintionClass) {
        this.defintionClass = defintionClass;
    }

    public static TriggerType getTypeForClass(
            final Class<? extends TriggerDefinition> defintionClass) {
        return Objects.requireNonNull(TRIGGER_TYPE_BY_DEFINITION_CLASS.get(defintionClass));
    }

    public Class<? extends TriggerDefinition> defintionClass() {
        return defintionClass;
    }
}
