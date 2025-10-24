package ch.ubique.backend.job.model.triggers;

import ch.ubique.backend.job.model.EmptyTriggerContext;
import ch.ubique.backend.job.model.FileTriggerContext;
import ch.ubique.backend.job.model.RetryTriggerContext;
import ch.ubique.backend.job.model.TriggerContext;

public enum TriggerContextType {
    EMPTY(EmptyTriggerContext.class),
    FILE(FileTriggerContext.class),
    RETRY(RetryTriggerContext.class);

    private final Class<? extends TriggerContext> contextClass;

    TriggerContextType(final Class<? extends TriggerContext> contextClass) {
        this.contextClass = contextClass;
    }

    public static TriggerContextType getTypeForClass(
            final Class<? extends TriggerContext> contextClass) {
        for (final var type : TriggerContextType.values()) {
            if (type.contextClass == contextClass) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                "Unknown trigger context class: " + contextClass.getName());
    }
}
