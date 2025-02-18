package ch.ubique.backend.job.logging.service;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;

import ch.ubique.backend.job.logging.LogLineAccumulator;
import ch.ubique.backend.job.logging.AccumulatedLogLines;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.stream.Stream;

@Service
public class LogFormatterService extends TextWebSocketHandler {

    private final PatternLayoutEncoder patternLayoutEncoder;

    public LogFormatterService() {
        patternLayoutEncoder = new PatternLayoutEncoder();
        patternLayoutEncoder.setPattern(
                "%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr(%5p) %clr(---){faint}"
                        + " %clr([%15.15t]){magenta} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %m");
        patternLayoutEncoder.setCharset(StandardCharsets.UTF_8);
        patternLayoutEncoder.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        patternLayoutEncoder.start();
    }

    public Stream<String> streamOfStrings(final Collection<? extends ILoggingEvent> loggingEvents) {
        return loggingEvents.stream().map(this::toString);
    }

    public String toString(final ILoggingEvent loggingEvent) {
        return new String(patternLayoutEncoder.encode(loggingEvent), StandardCharsets.UTF_8);
    }

    public AccumulatedLogLines accumulatedLogLines(
            final Collection<? extends ILoggingEvent> loggingEvents) {
        return streamOfStrings(loggingEvents)
                .reduce(
                        new LogLineAccumulator(),
                        LogLineAccumulator::accumulate,
                        LogLineAccumulator::combine)
                .collectLog();
    }
}
