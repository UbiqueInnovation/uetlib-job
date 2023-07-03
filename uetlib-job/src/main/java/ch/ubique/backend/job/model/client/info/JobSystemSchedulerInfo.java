package ch.ubique.backend.job.model.client.info;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;

public record JobSystemSchedulerInfo(
        @Valid @NotNull JobSystemSchedulerMetaData metaData,
        @Valid @NotEmpty Collection<JobSystemSchedulerGroupInfo> groups) {}
