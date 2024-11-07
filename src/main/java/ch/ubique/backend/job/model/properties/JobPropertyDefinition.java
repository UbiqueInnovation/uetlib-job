package ch.ubique.backend.job.model.properties;

import ch.ubique.backend.core.utils.StringEncoderDecoder;

import java.util.Optional;
import java.util.Set;

public sealed interface JobPropertyDefinition<T> extends StringEncoderDecoder<T>
        permits AbstractJobPropertyDefinition {

    String key();

    String description();

    JobPropertyDefinitionGroup group();

    boolean nullable();

    Optional<T> defaultValue();

    Optional<Set<T>> allowedValues();

    JobProperty<T> createPropertyFromString(final String value);

    JobProperty<T> createProperty(final T value);
}
