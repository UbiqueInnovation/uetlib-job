package ch.ubique.backend.job.model.properties;

import ch.ubique.backend.core.utils.StringUtils;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

abstract sealed class AbstractJobPropertyDefinition<T> implements JobPropertyDefinition<T>
        permits BooleanPropertyDefinition,
                DurationPropertyDefinition,
                EnumPropertyDefinition,
                GenericPropertyDefinition,
                IntPropertyDefinition,
                StringPropertyDefinition {

    @NotBlank private final String key;

    @NotBlank private final String description;

    @NotNull private final JobPropertyDefinitionGroup group;

    private final boolean nullable;

    @Nullable private final T defaultValue;

    @Nullable private final Set<T> allowedValues;

    protected AbstractJobPropertyDefinition(
            @NotBlank final String key,
            @NotBlank final String description,
            @NotNull final JobPropertyDefinitionGroup group,
            final boolean nullable,
            @Nullable final T defaultValue,
            @Nullable final Set<T> allowedValues) {
        this.key = StringUtils.requireNonBlank(key);
        this.description = StringUtils.requireNonBlank(description);
        this.group = group;
        this.nullable = nullable;
        this.defaultValue = defaultValue;
        this.allowedValues = allowedValues;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public JobPropertyDefinitionGroup group() {
        return group;
    }

    @Override
    public boolean nullable() {
        return nullable;
    }

    @Override
    public Optional<T> defaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    @Override
    public Optional<Set<T>> allowedValues() {
        return Optional.ofNullable(allowedValues);
    }

    @Override
    public final JobProperty<T> createPropertyFromString(final String value) {
        if (org.apache.commons.lang3.StringUtils.isBlank(value)) {
            return createProperty(null);
        }
        return createProperty(decodeFromString(value));
    }

    @Override
    public final JobProperty<T> createProperty(final T value) {
        return new JobProperty<>(this, value);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("key", key)
                .append("description", description)
                .append("group", group)
                .append("nullable", nullable)
                .append("defaultValue", defaultValue)
                .append("allowedValues", allowedValues)
                .toString();
    }
}
