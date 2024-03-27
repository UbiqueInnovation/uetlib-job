package ch.ubique.backend.job.model.triggers;

import ch.ubique.backend.job.Job;

import jakarta.validation.constraints.NotNull;

public record DatabaseSubmissionTrigger(@NotNull Class<? extends Job> jobClass)
        implements TriggerDefinition {}
