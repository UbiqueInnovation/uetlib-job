package ch.ubique.backend.job.model.properties;

import ch.ubique.backend.job.triggers.Trigger;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public final class JobTriggers {

    private final boolean isFixed;
    private final Collection<Trigger> triggers;

    private JobTriggers(final boolean isFixed, @NotNull final Collection<Trigger> triggers) {
        this.isFixed = isFixed;
        this.triggers = Collections.unmodifiableCollection(triggers);
    }

    public static JobTriggers unset() {
        return new JobTriggers(false, Set.of());
    }

    public static JobTriggers initial(final Trigger trigger) {
        return initial(Set.of(trigger));
    }
    
    public static JobTriggers initial(@NotEmpty final Collection<Trigger> triggers) {
        return new JobTriggers(false, triggers);
    }

    public static JobTriggers fixed(final Trigger trigger) {
        return fixed(Set.of(trigger));
    }

    public static JobTriggers fixed(@NotEmpty final Collection<Trigger> triggers) {
        return new JobTriggers(true, triggers);
    }

    public boolean isFixed() {
        return isFixed;
    }

    public Collection<Trigger> triggers() {
        return triggers;
    }
}
