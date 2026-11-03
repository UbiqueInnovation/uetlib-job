package ch.ubique.uetlib.job.model;

import ch.ubique.uetlib.job.model.properties.JobProperties;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobRunContext(
        @JsonProperty(required = true) @NotNull @Valid JobClassAndId job,
        @JsonProperty(required = true) @NotNull @Valid JobRun jobRun,
        @JsonProperty(required = true) @NotNull @Valid JobRunArguments arguments,
        @JsonProperty(required = true) @NotNull @Valid JobProperties properties) {

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("runId", jobRun.id())
                .append("arguments", arguments)
                .toString();
    }
}
