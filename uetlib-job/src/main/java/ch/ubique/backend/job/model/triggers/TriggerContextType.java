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
        return switch (contextClass
                .getCanonicalName()) { // TODO: Java 21 Switch Pattern Matching without throw
            case "ch.ubique.backend.job.model.EmptyTriggerContext" -> TriggerContextType.EMPTY;
            case "ch.ubique.backend.job.model.FileTriggerContext" -> TriggerContextType.FILE;
            default -> throw new IllegalArgumentException(
                    "Unknown trigger context type: " + contextClass.getCanonicalName());
        };
    }

    public Class<? extends TriggerContext> contextClass() {
        return contextClass;
    }
}
