package ch.ubique.backend.job.model.properties;

import ch.ubique.backend.core.utils.StringEncoderDecoder;
import ch.ubique.backend.core.utils.StringUtils;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Optional;
import java.util.Set;

public abstract sealed class JobPropertyDefinition<T> implements StringEncoderDecoder<T>
        permits DurationPropertyDefinition,
                EnumPropertyDefinition,
                IntPropertyDefinition,
                StringPropertyDefinition {

    @NotNull private final String key;

    @NotBlank private final String description;

    private final boolean nullable;

    @Nullable private final T defaultValue;

    @Nullable private final Set<T> allowedValues;

    protected JobPropertyDefinition(
            @NotNull final String key,
            @NotBlank final String description,
            final boolean nullable,
            @Nullable final T defaultValue,
            @Nullable final Set<T> allowedValues) {

        this.key = StringUtils.requireNonBlank(key);
        this.description = StringUtils.requireNonBlank(description);
        this.nullable = nullable;
        this.defaultValue = defaultValue;
        this.allowedValues = allowedValues;
    }

    public String key() {
        return key;
    }

    public String description() {
        return description;
    }

    public boolean nullable() {
        return nullable;
    }

    public Optional<T> defaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    public Optional<Set<T>> allowedValues() {
        return Optional.ofNullable(allowedValues);
    }

    public final JobProperty<T> createPropertyFromString(final String value) {
        if (org.apache.commons.lang3.StringUtils.isBlank(value)) {
            return createProperty(null);
        }
        return createProperty(decode(value));
    }

    public final JobProperty<T> createProperty(final T value) {
        return new JobProperty<>(this, value);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("key", key)
                .append("nullable", nullable)
                .append("defaultValue", defaultValue)
                .append("allowedValues", allowedValues)
                .toString();
    }
}
