package ch.ubique.backend.job.model.properties;

import jakarta.validation.constraints.NotNull;

import java.util.Collection;

public record JobProperties(@NotNull Collection<JobProperty<Object>> values) {

    public <T> JobProperty<T> get(final JobPropertyDefinition<T> definition) {
        //noinspection unchecked
        return values.stream()
                .filter(property -> property.key().equals(definition.key()))
                .findFirst()
                .map(p -> (JobProperty<T>) p)
                .orElseGet(() -> definition.createProperty(null));
    }
}
