package ch.ubique.uetlib.job.model;

import ch.ubique.uetlib.core.utils.ArrayUtils;
import ch.ubique.uetlib.job.Job;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
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
