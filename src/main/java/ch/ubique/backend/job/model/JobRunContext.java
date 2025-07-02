package ch.ubique.backend.job.model;

import ch.ubique.backend.job.model.properties.JobProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public record JobRunContext(
        @NotNull @Valid JobClassAndId job,
        @NotNull @Valid JobRun jobRun,
        @NotNull @Valid JobRunArguments arguments,
        @NotNull @Valid JobProperties properties) {

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("runId", jobRun.id())
                .append("arguments", arguments)
                .toString();
    }
}
