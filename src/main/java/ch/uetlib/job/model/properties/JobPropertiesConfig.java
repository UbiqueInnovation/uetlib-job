package ch.uetlib.job.model.properties;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

public final class JobPropertiesConfig {

    private final boolean isFixed;
    private final Collection<JobProperty<Object>> properties;

    private JobPropertiesConfig(
            final boolean isFixed, final Collection<JobProperty<Object>> properties) {
        this.isFixed = isFixed;
        this.properties = Collections.unmodifiableCollection(properties);
    }

    public static JobPropertiesConfig unset() {
        return new JobPropertiesConfig(false, Set.of());
    }

    public static JobPropertiesConfig initial(final JobProperty<?>... properties) {
        //noinspection unchecked
        return initial(Stream.of(properties).map(p -> (JobProperty<Object>) p).toList());
    }

    public static JobPropertiesConfig initial(final Collection<JobProperty<Object>> properties) {
        return new JobPropertiesConfig(false, properties);
    }

    public static JobPropertiesConfig fixed(final JobProperty<?>... properties) {
        //noinspection unchecked
        return fixed(Stream.of(properties).map(p -> (JobProperty<Object>) p).toList());
    }

    public static JobPropertiesConfig fixed(final Collection<JobProperty<Object>> properties) {
        return new JobPropertiesConfig(true, properties);
    }

    public boolean isFixed() {
        return isFixed;
    }

    public Collection<JobProperty<Object>> properties() {
        return properties;
    }
}
