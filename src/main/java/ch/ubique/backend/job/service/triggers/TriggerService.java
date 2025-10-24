package ch.ubique.backend.job.service.triggers;

import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.triggers.TriggerDefinition;
import ch.ubique.backend.job.model.triggers.TriggerType;

import org.springframework.core.GenericTypeResolver;

import java.util.function.Consumer;

public interface TriggerService<T extends TriggerDefinition> {

    default void onStartup(
            final int triggerId,
            final T triggerDefinition,
            final JobClassAndId job,
            final Consumer<JobRunArguments> consumer) {
        addTrigger(triggerId, triggerDefinition, job, consumer);
    }

    void addTrigger(
            final int triggerId,
            final T triggerDefinition,
            final JobClassAndId job,
            final Consumer<JobRunArguments> consumer);

    void removeTrigger(final int triggerId);

    default TriggerType triggerType() {
        //noinspection unchecked
        final var genericType =
                (Class<T>)
                        GenericTypeResolver.resolveTypeArgument(getClass(), TriggerService.class);
        return TriggerType.getTypeForClass(genericType);
    }
}
