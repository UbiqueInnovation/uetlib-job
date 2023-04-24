package ch.ubique.backend.job.model.client;

import ch.ubique.backend.job.model.properties.JobPropertyDefinitionGroup;

import jakarta.validation.constraints.NotBlank;

public record JobPropertyGroupInfo(@NotBlank String key, @NotBlank String title) {

    public JobPropertyGroupInfo(final JobPropertyDefinitionGroup group) {
        this(group.name(), group.title());
    }
}
