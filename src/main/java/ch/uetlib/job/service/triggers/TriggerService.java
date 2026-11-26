package ch.uetlib.job.service.triggers;

import static java.util.Objects.requireNonNull;

import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.triggers.TriggerDefinition;
import ch.uetlib.job.model.triggers.TriggerType;

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

    /**
     * Whether this instance holds the primary lease, as of its latest check-in; called at every
     * check-in once the triggers are registered. Only a hint for scheduling - a fire still only
     * queues a run if the database confirms the lease.
     */
    default void onPrimaryLease(final boolean holdsLease) {}

    default TriggerType triggerType() {
        //noinspection unchecked
        final var genericType =
                (Class<T>)
                        GenericTypeResolver.resolveTypeArgument(getClass(), TriggerService.class);
        return TriggerType.getTypeForClass(requireNonNull(genericType));
    }
}
