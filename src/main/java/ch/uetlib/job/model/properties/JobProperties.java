package ch.uetlib.job.model.properties;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import java.util.Collection;

public record JobProperties(
        @JsonProperty(required = true) @NotNull Collection<JobProperty<Object>> values) {

    public <T> JobProperty<T> get(final JobPropertyDefinition<T> definition) {
        //noinspection unchecked
        return values.stream()
                .filter(property -> property.key().equals(definition.key()))
                .findFirst()
                .map(p -> (JobProperty<T>) p)
                .orElseGet(() -> definition.createProperty(null));
    }

    public <T> @Nullable T getValue(final JobPropertyDefinition<T> definition) {
        return get(definition).value();
    }
}
