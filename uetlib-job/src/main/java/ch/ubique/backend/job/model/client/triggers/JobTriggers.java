package ch.ubique.backend.job.model.client.triggers;

import jakarta.validation.constraints.NotNull;

import java.util.Collection;

public record JobTriggers(boolean isFixed, @NotNull Collection<JobTrigger> triggers) {}
