package ch.ubique.backend.job.model.triggers;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public final class JobTriggersConfig {

    private final boolean isFixed;
    private final Collection<Trigger> triggers;

    private JobTriggersConfig(final boolean isFixed, @NotNull final Collection<Trigger> triggers) {
        this.isFixed = isFixed;
        this.triggers = Collections.unmodifiableCollection(triggers);
    }

    public static JobTriggersConfig unset() {
        return new JobTriggersConfig(false, Set.of());
    }

    public static JobTriggersConfig initial(final Trigger trigger) {
        return initial(Set.of(trigger));
    }

    public static JobTriggersConfig initial(@NotEmpty final Collection<Trigger> triggers) {
        return new JobTriggersConfig(false, triggers);
    }

    public static JobTriggersConfig fixed(final Trigger trigger) {
        return fixed(Set.of(trigger));
    }

    public static JobTriggersConfig fixed(@NotEmpty final Collection<Trigger> triggers) {
        return new JobTriggersConfig(true, triggers);
    }

    public boolean isFixed() {
        return isFixed;
    }

    public Collection<Trigger> triggers() {
        return triggers;
    }
}
