package ch.ubique.backend.job.service.triggers;

import ch.ubique.backend.job.model.triggers.TriggerDefinition;
import ch.ubique.backend.job.model.triggers.TriggerType;

import org.springframework.core.GenericTypeResolver;

public interface TriggerService<T extends TriggerDefinition> {

    void onStartup(final int triggerId, final T triggerDefinition, final Runnable runnable);

    void addTrigger(final int triggerId, final T triggerDefinition, final Runnable runnable);

    void removeTrigger(final int triggerId);

    default TriggerType triggerType() {
        //noinspection unchecked
        final var genericType =
                (Class<T>)
                        GenericTypeResolver.resolveTypeArgument(getClass(), TriggerService.class);
        return TriggerType.getTypeForClass(genericType);
    }
}
