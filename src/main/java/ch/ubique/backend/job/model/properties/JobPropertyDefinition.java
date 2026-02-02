package ch.ubique.backend.job.model.properties;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Optional;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface JobPropertyDefinition<T> extends StringEncoderDecoder<T>
        permits AbstractJobPropertyDefinition {

    @NotBlank
    @JsonProperty(required = true)
    String key();

    @NotBlank
    @JsonProperty(required = true)
    String description();

    @NotNull
    @JsonProperty(required = true)
    JobPropertyDefinitionGroup group();

    @JsonProperty(required = true)
    boolean nullable();

    @NotNull
    @JsonProperty
    Optional<T> defaultValue();

    @NotNull
    @JsonProperty
    Optional<Set<T>> allowedValues();

    @JsonIgnore
    JobProperty<T> createPropertyFromString(final String value);

    @JsonIgnore
    JobProperty<T> createProperty(final T value);
}
