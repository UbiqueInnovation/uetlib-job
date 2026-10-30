package ch.ubique.uetlib.job.model.properties;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface JobPropertyDefinition<T extends @Nullable Object>
        extends StringEncoderDecoder<T> permits AbstractJobPropertyDefinition {

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

    @JsonProperty
    Optional<@NonNull T> defaultValue();

    @JsonProperty
    Optional<Set<T>> allowedValues();

    @JsonIgnore
    JobProperty<T> createPropertyFromString(final @Nullable String value);

    @JsonIgnore
    JobProperty<T> createProperty(final @Nullable T value);
}
