package ch.ubique.backend.job.model.client.info;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.Collection;

public record JobSystemSchedulerInfo(
        @NotEmpty Collection<@Valid JobSystemRunnerGroupInfo> groups) {}
