package ch.ubique.backend.job.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.exception.WebSocketException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class BackendLibJobWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(BackendLibJobWebSocketHandler.class);
    private static final int PAGE_SIZE = 50000;

    private final ObjectMapper objectMapper;
    private final JobDataService jobDataService;

    private final PatternLayoutEncoder encoder;

    private final Map<String, Triple<WebSocketSession, Integer, AtomicLong>> activeSessions =
            new ConcurrentHashMap<>();

    public BackendLibJobWebSocketHandler(
            final ObjectMapper objectMapper, final JobDataService jobDataService) {
        this.objectMapper = objectMapper;
        this.jobDataService = jobDataService;
        encoder = new PatternLayoutEncoder();
        encoder.setPattern(
                "%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr(%5p) %clr(---){faint} %clr([%15.15t]){magenta} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %m");
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        encoder.start();
    }

    @Override
    public void handleTextMessage(final WebSocketSession session, final TextMessage message)
            throws IOException {
        final var jobRunId = objectMapper.readValue(message.getPayload(), Message.class).jobRunId;
        logger.trace("message with jobRunId: {}", jobRunId);
        activeSessions.putIfAbsent(
                session.getId(), Triple.of(session, jobRunId, new AtomicLong(0)));
    }

    @Override
    public void afterConnectionClosed(
            final WebSocketSession session, final @NotNull CloseStatus status) throws Exception {
        activeSessions.remove(session.getId());
        super.afterConnectionClosed(session, status);
    }

    @SuppressWarnings("java:S3864")
    public void sendLog() {
        logger.trace("send log to all active sessions");
        activeSessions.forEach(
                (sessionId, pair) -> {
                    final var session = pair.getLeft();
                    final var jobRunId = pair.getMiddle();
                    final var counter = pair.getRight();
                    getLogLines(jobRunId)
                            .flatMap(
                                    lines ->
                                            lines.skip(counter.get())
                                                    .limit(PAGE_SIZE)
                                                    .peek(line -> counter.incrementAndGet())
                                                    .reduce((acc, line) -> acc + "\n" + line))
                            .map(TextMessage::new)
                            .ifPresent(
                                    message -> {
                                        try {
                                            logger.trace(
                                                    "sending log with length {} to session {}",
                                                    message.getPayloadLength(),
                                                    sessionId);
                                            session.sendMessage(message);
                                        } catch (final IOException e) {
                                            throw new WebSocketException(e);
                                        }
                                    });
                });
    }

    @NotNull
    private Optional<Stream<String>> getLogLines(final Integer jobRunId) {

        final var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger("ROOT").getAppender("console");

        return Optional.of(
                jobDataService.findLogsForJobRun(jobRunId).stream()
                        .map(encoder::encode)
                        .map(bytes -> new String(bytes, StandardCharsets.UTF_8)));
    }

    record Message(int jobRunId) {}
}
