package ch.ubique.backend.job.model.properties;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

public final class JobProperties {

    private final boolean isFixed;
    private final Collection<JobProperty<Object>> values;

    private JobProperties(final boolean isFixed, final Collection<JobProperty<Object>> values) {
        this.isFixed = isFixed;
        this.values = Collections.unmodifiableCollection(values);
    }

    public static JobProperties unset() {
        return new JobProperties(false, Set.of());
    }

    public static JobProperties initial(final JobProperty<?>... properties) {
        //noinspection unchecked
        return initial(Stream.of(properties).map(p -> (JobProperty<Object>) p).toList());
    }

    public static JobProperties initial(final Collection<JobProperty<Object>> properties) {
        return new JobProperties(false, properties);
    }

    public static JobProperties fixed(final JobProperty<?>... properties) {
        //noinspection unchecked
        return fixed(Stream.of(properties).map(p -> (JobProperty<Object>) p).toList());
    }

    public static JobProperties fixed(final Collection<JobProperty<Object>> properties) {
        return new JobProperties(true, properties);
    }

    public boolean isFixed() {
        return isFixed;
    }

    public Collection<JobProperty<Object>> values() {
        return values;
    }
}
