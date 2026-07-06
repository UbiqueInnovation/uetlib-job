package ch.ubique.backend.job.model.triggers;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public final class JobTriggersConfig {

    private final boolean isFixed;
    private final Collection<TriggerDefinition> triggerDefinitions;

    private JobTriggersConfig(
            final boolean isFixed,
             final Collection<TriggerDefinition> triggerDefinitions) {
        this.isFixed = isFixed;
        this.triggerDefinitions = Collections.unmodifiableCollection(triggerDefinitions);
    }

    public static JobTriggersConfig unset() {
        return new JobTriggersConfig(false, Set.of());
    }

    public static JobTriggersConfig initial(final TriggerDefinition... triggerDefinition) {
        return initial(Set.of(triggerDefinition));
    }

    public static JobTriggersConfig initial(
            @NotEmpty final Collection<TriggerDefinition> triggerDefinitions) {
        return new JobTriggersConfig(false, triggerDefinitions);
    }

    public static JobTriggersConfig fixed(final TriggerDefinition... triggerDefinition) {
        return fixed(Set.of(triggerDefinition));
    }

    public static JobTriggersConfig fixed(
            @NotEmpty final Collection<TriggerDefinition> triggerDefinitions) {
        return new JobTriggersConfig(true, triggerDefinitions);
    }

    public boolean isFixed() {
        return isFixed;
    }

    public Collection<TriggerDefinition> triggers() {
        return triggerDefinitions;
    }
}
