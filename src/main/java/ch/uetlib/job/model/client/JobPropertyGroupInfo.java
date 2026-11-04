package ch.uetlib.job.model.client;

import ch.uetlib.job.model.properties.JobPropertyDefinitionGroup;

import jakarta.validation.constraints.NotBlank;

import org.apache.commons.lang3.StringUtils;

public record JobPropertyGroupInfo(@NotBlank String key, @NotBlank String title) {

    public JobPropertyGroupInfo(final JobPropertyDefinitionGroup group) {
        this(group.name(), StringUtils.capitalize(group.name().toLowerCase()));
    }
}
