package ch.ubique.backend.job.model.client.info;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record JobSystemInfo(
        @Valid @NotNull JobSystemCronInfo cron,
        @NotNull List<@Valid JobSystemRunnerGroupInfo> runnerGroups) {}
