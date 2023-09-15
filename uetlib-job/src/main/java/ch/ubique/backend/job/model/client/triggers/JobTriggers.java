package ch.ubique.backend.job.model.client.triggers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;

public record JobTriggers(boolean isFixed, @NotNull @Valid Collection<JobTrigger> triggers) {}
