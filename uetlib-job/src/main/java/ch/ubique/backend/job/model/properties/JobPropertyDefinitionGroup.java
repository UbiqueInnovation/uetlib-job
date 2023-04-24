package ch.ubique.backend.job.model.properties;

import jakarta.validation.constraints.NotBlank;

public enum JobPropertyDefinitionGroup implements Comparable<JobPropertyDefinitionGroup> {
    RUNNER("Runner"),
    MONITORING("Monitoring"),
    CLEANUP("Cleanup");

    @NotBlank private final String title;

    JobPropertyDefinitionGroup(@NotBlank final String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
