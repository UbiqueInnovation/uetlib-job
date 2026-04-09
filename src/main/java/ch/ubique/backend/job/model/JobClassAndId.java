package ch.ubique.backend.job.model;

import ch.ubique.backend.core.utils.ArrayUtils;
import ch.ubique.backend.job.Job;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@NullMarked
public record JobClassAndId(
        @JsonProperty(required = true) int id,
        @JsonProperty(required = true) @NotBlank String canonicalClassName) {

    public JobClassAndId(final int id, final Class<? extends Job> jobClass) {
        this(id, jobClass.getCanonicalName());
    }

    @JsonProperty(required = true)
    @NotBlank
    public String name() {
        return ArrayUtils.lastElement(canonicalClassName.split("\\."));
    }
}
