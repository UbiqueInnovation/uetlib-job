package ch.ubique.backend.job.model.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.EnumUtils;

import java.util.Objects;
import java.util.Set;

final class EnumPropertyDefinition<T extends Enum<T>> extends AbstractJobPropertyDefinition<T> {

    private final Class<T> enumClass;

    @SuppressWarnings("unchecked")
    EnumPropertyDefinition(
            @NotBlank final String key,
            @NotBlank final String description,
            @NotNull final JobPropertyDefinitionGroup group,
            @NotNull final T defaultValue) {
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
    public T decode(final String value) {
        return Objects.requireNonNull(EnumUtils.getEnumIgnoreCase(enumClass, value));
    }

    @Override
    public String encode(final T value) {
        return value.name();
    }
}
