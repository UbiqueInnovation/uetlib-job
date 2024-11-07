package ch.ubique.backend.job.logging;

import ch.ubique.backend.core.validator.NullOrNotBlank;

import jakarta.validation.constraints.PositiveOrZero;

import org.apache.commons.lang3.StringUtils;

public class LogLineAccumulator {
    private int lines;
    private StringBuilder logBuilder = new StringBuilder();

    public static LogLineAccumulator combine(
            final LogLineAccumulator acc1, final LogLineAccumulator acc2) {
        final var acc = new LogLineAccumulator();
        acc.lines = acc1.lines + acc2.lines;
        acc.logBuilder = acc1.logBuilder.append("\n").append(acc2.logBuilder);
        return acc;
    }

    public LogLineAccumulator accumulate(final String line) {
        lines++;
        logBuilder.append(line).append("\n");
        return this;
    }

    public AccumulatedLogLines collectLog() {
        return new AccumulatedLogLines(lines, StringUtils.stripToNull(logBuilder.toString()));
    }

    public record AccumulatedLogLines(@PositiveOrZero int lines, @NullOrNotBlank String log) {}
}
