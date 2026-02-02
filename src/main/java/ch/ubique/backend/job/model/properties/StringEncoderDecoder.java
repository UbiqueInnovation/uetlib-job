package ch.ubique.backend.job.model.properties;

public interface StringEncoderDecoder<T> {

    T decodeFromString(final String value);

    String encodeFromString(final T value);
}