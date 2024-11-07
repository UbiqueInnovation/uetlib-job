package ch.ubique.backend.job.model;

import ch.ubique.backend.core.utils.ArrayUtils;

import jakarta.validation.constraints.NotBlank;

public record JobClassAndId(int id, @NotBlank String canonicalClassName) {

    public String name() {
        return ArrayUtils.lastElement(canonicalClassName.split("\\."));
    }
}
