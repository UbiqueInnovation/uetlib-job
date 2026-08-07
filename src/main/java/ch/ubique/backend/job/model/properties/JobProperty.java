package ch.ubique.backend.job.model.properties;

import static ch.ubique.backend.core.utils.OptionalUtils.ofNullable;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.Optional;

public record JobProperty<T extends @Nullable Object>(
        @JsonProperty(required = true) @NotNull JobPropertyDefinition<T> definition,
        @Nullable T value) {

    public JobProperty(
            @JsonProperty(required = true) @NotNull
                    final JobPropertyDefinition<@Valid T> definition,
            @Nullable final T value) {
        this.definition = requireNonNull(definition);
        if (definition.nullable()) {
            this.value = value;
        } else {
            this.value =
                    ofNullable(value)
                            .or(definition::defaultValue)
                            .orElseThrow(
                                    () ->
                                            new NoSuchElementException(
                                                    "Either value or default value must be present for definition: "
                                                            + definition.key()));
        }
    }

    public String key() {
        return definition().key();
    }

    public boolean isDefault() {
        return value == null || definition.defaultValue().map(value::equals).orElse(false);
    }

    @Nullable
    public String encodedValue() {
        return Optional.ofNullable(value).map(definition::encodeFromString).orElse(null);
    }

    public boolean isValuePresent() {
        return value != null;
    }

    public Optional<@NonNull T> valueMaybe() {
        return ofNullable(value);
    }

    public @NonNull T valueNonNull() {
        return requireNonNull(value);
    }
}
