package ch.ubique.backend.job.model.properties;

import ch.ubique.backend.core.utils.StringEncoderDecoder;

import org.apache.commons.lang3.EnumUtils;

import java.util.Objects;
import java.util.Set;

final class EnumPropertyDefinition<T extends Enum<T>> extends JobPropertyDefinition<T> {

    private final Class<T> enumClass;

    private final StringEncoderDecoder<T> customEncoderDecoder;

    EnumPropertyDefinition(final String key, final String description, final T defaultValue) {
        this(key, description, defaultValue, null);
    }

    @SuppressWarnings("unchecked")
    EnumPropertyDefinition(
            final String key,
            final String description,
            final T defaultValue,
            final StringEncoderDecoder<T> customEncoderDecoder) {
        super(
                key,
                description,
                false,
                Objects.requireNonNull(defaultValue),
                (Set<T>) Set.of(defaultValue.getClass().getEnumConstants()));
        this.enumClass = (Class<T>) defaultValue.getClass();
        this.customEncoderDecoder = customEncoderDecoder;
    }

    @Override
    public T decode(final String value) {
        if (customEncoderDecoder != null) {
            return customEncoderDecoder.decode(value);
        }
        return Objects.requireNonNull(EnumUtils.getEnumIgnoreCase(enumClass, value));
    }

    @Override
    public String encode(final T value) {
        if (customEncoderDecoder != null) {
            return customEncoderDecoder.encode(value);
        }
        return value.name();
    }
}
