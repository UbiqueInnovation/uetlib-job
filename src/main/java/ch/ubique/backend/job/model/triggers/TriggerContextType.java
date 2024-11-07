package ch.ubique.backend.job.model.triggers;

import ch.ubique.backend.job.model.EmptyTriggerContext;
import ch.ubique.backend.job.model.FileTriggerContext;
import ch.ubique.backend.job.model.TriggerContext;

public enum TriggerContextType {
    EMPTY(EmptyTriggerContext.class),
    FILE(FileTriggerContext.class);

    private final Class<? extends TriggerContext> contextClass;

    TriggerContextType(final Class<? extends TriggerContext> contextClass) {
        this.contextClass = contextClass;
    }

    public static TriggerContextType getTypeForClass(
            final Class<? extends TriggerContext> contextClass) {
        if (contextClass == EmptyTriggerContext.class) {
            return EMPTY;
        } else if (contextClass == FileTriggerContext.class) {
            return FILE;
        }
        throw new IllegalArgumentException(
                "Unknown trigger context class: " + contextClass.getName());
    }

    public Class<? extends TriggerContext> contextClass() {
        return contextClass;
    }
}
