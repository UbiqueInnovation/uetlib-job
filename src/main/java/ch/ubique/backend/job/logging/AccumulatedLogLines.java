package ch.ubique.backend.job.logging;

import ch.ubique.backend.core.validator.NullOrNotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record AccumulatedLogLines(@PositiveOrZero int lines, @NullOrNotBlank String log) {}
