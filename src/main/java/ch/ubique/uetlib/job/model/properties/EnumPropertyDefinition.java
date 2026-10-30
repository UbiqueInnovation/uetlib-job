package ch.ubique.uetlib.job.model.properties;

import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.EnumUtils;
import org.jspecify.annotations.Nullable;

final class EnumPropertyDefinition<T extends Enum<T>> extends AbstractJobPropertyDefinition<T> {

    private final Class<T> enumClass;

    @SuppressWarnings("unchecked")
    EnumPropertyDefinition(
            final String key,
            final String description,
            final JobPropertyDefinitionGroup group,
            final T defaultValue) {
        super(
                key,
                description,
                group,
                false,
                Objects.requireNonNull(defaultValue),
                (Set<T>) Set.of(defaultValue.getClass().getEnumConstants()));
        this.enumClass = (Class<T>) defaultValue.getClass();
    }

    @Override
    public T decodeFromString(final @Nullable String value) {
        return Objects.requireNonNull(EnumUtils.getEnumIgnoreCase(enumClass, value));
    }

    @Override
    public String encodeFromString(final T value) {
        return value.name();
    }
}
