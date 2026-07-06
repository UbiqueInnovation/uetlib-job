package ch.ubique.backend.job.model.properties;

import org.jspecify.annotations.Nullable;

public interface StringEncoderDecoder<T extends @Nullable Object> {

    T decodeFromString(final @Nullable String value);

    @Nullable String encodeFromString(final T value);
}
