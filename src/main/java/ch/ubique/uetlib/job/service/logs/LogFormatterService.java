package ch.ubique.uetlib.job.service.logs;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.ubique.uetlib.job.logging.AccumulatedLogLines;
import ch.ubique.uetlib.job.logging.LoggingEvent;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    public String toFormattedMessage(final ILoggingEvent event) {
        return encodeAsUtf8(patternLayoutEncoder.encode(event));
    }

    private static String encodeAsUtf8(final byte[] messageBytes) {
        final var value = new String(messageBytes, StandardCharsets.UTF_8);
        if (value.contains("\0")) {
            return value.replace('\0', '\uFFFD');
        }
        return value;
    }

    public Stream<String> streamOfStrings(final Collection<LoggingEvent> loggingEvents) {
        return loggingEvents.stream().map(this::toString);
    }

    private String toString(final LoggingEvent loggingEvent) {
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
