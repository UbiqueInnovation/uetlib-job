package ch.uetlib.job.model.properties;

import org.apache.commons.lang3.EnumUtils;

import java.util.Objects;
import java.util.Set;

final class EnumPropertyDefinition<T extends Enum<T>> extends AbstractJobPropertyDefinition<T> {

    private final Class<T> enumClass;

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
                Set.of(defaultValue.getDeclaringClass().getEnumConstants()));
        this.enumClass = defaultValue.getDeclaringClass();
    }

    @Override
    public T decodeFromString(final String value) {
        return Objects.requireNonNull(EnumUtils.getEnumIgnoreCase(enumClass, value));
    }

    @Override
    public String encodeFromString(final T value) {
        return value.name();
    }
}
