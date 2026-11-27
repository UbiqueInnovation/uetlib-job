package ch.uetlib.job.model.properties;

import static ch.uetlib.core.utils.OptionalUtils.ofNullable;

import ch.uetlib.core.utils.StringUtils;

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

    private final String key;
    private final String description;
    private final JobPropertyDefinitionGroup group;
    private final boolean nullable;
    @Nullable private final T defaultValue;
    @Nullable private final Set<T> allowedValues;

    protected AbstractJobPropertyDefinition(
            final String key,
            final String description,
            final JobPropertyDefinitionGroup group,
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
    @SuppressWarnings("NullAway")
    public Optional<T> defaultValue() {
        return ofNullable(defaultValue);
    }

    @Override
    public Optional<Set<T>> allowedValues() {
        return Optional.ofNullable(allowedValues);
    }

    @Override
    public final JobProperty<T> createPropertyFromString(final @Nullable String value) {
        if (org.apache.commons.lang3.StringUtils.isBlank(value)) {
            return createProperty(null);
        }
        return createProperty(decodeFromString(value));
    }

    @Override
    public final JobProperty<T> createProperty(final @Nullable T value) {
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
