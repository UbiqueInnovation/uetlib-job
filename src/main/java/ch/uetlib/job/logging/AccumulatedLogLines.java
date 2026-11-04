package ch.uetlib.job.logging;

import jakarta.validation.constraints.PositiveOrZero;

public record AccumulatedLogLines(@PositiveOrZero int lines, String log) {}
