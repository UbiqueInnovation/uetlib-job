package ch.ubique.backend.job.model.properties;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;
import java.util.Optional;

public record JobProperty<T>(@NotNull JobPropertyDefinition<T> definition, T value) {

    public JobProperty(
            @NotNull @Valid final JobPropertyDefinition<T> definition, @Nullable final T value) {
        this.definition = Objects.requireNonNull(definition);
        if (definition.nullable()) {
            this.value = value;
        } else {
            this.value = Objects.requireNonNullElse(value, definition.defaultValue().orElse(null));
        }
    }

    public String key() {
        return definition().key();
    }

    public boolean isDefault() {
        return value == null || definition.defaultValue().map(value::equals).orElse(false);
    }

    public String encodedValue() {
        return Optional.ofNullable(value).map(definition::encode).orElse(null);
    }

    public boolean isValuePresent() {
        return value != null;
    }

    public Optional<T> valueMaybe() {
        return Optional.ofNullable(value);
    }
}
