package ch.ubique.backend.job.service.logs;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.ubique.backend.job.logging.AccumulatedLogLines;
import ch.ubique.backend.job.logging.LoggingEvent;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class LogFormatterService {

    private final PatternLayoutEncoder patternLayoutEncoder;
    private final DateTimeFormatter dateTimeFormatter;

    public LogFormatterService() {
        dateTimeFormatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                        .withZone(ZoneOffset.ofHours(0));

        patternLayoutEncoder = new PatternLayoutEncoder();
        patternLayoutEncoder.setPattern(
                "%clr(%5p) %clr(---){faint}"
                        + " %clr([%15.15t]){magenta} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %m");
        patternLayoutEncoder.setCharset(StandardCharsets.UTF_8);
        patternLayoutEncoder.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        patternLayoutEncoder.start();
    }

    /** Build formatted log message. Includes stack traces for exceptions. */
    public String toFormattedMessage(ILoggingEvent event) {
        return new String(patternLayoutEncoder.encode(event), StandardCharsets.UTF_8);
    }

    public Stream<String> streamOfStrings(Collection<LoggingEvent> loggingEvents) {
        return loggingEvents.stream().map(this::toString);
    }

    private String toString(LoggingEvent loggingEvent) {
        return formatTimestamp(loggingEvent) + " " + loggingEvent.formattedMessage();
    }

    private String formatTimestamp(final LoggingEvent loggingEvent) {
        return dateTimeFormatter.format(loggingEvent.timestamp());
    }

    public AccumulatedLogLines accumulatedLogLines(final Collection<LoggingEvent> loggingEvents) {
        return new AccumulatedLogLines(
                loggingEvents.size(),
                streamOfStrings(loggingEvents).collect(Collectors.joining("\n")));
    }
}
