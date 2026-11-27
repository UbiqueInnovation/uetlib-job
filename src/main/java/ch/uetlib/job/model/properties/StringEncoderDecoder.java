package ch.uetlib.job.model.properties;

import org.jspecify.annotations.Nullable;

public interface StringEncoderDecoder<T extends @Nullable Object> {

    T decodeFromString(final String value);

    String encodeFromString(final T value);
}
