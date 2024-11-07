package ch.ubique.backend.job.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.exception.WebSocketException;
import ch.ubique.backend.job.logging.LogLineAccumulator;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Profile("!job-run")
public class BackendLibJobWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(BackendLibJobWebSocketHandler.class);
    private static final int PAGE_SIZE = 5000;

    private final ObjectMapper objectMapper;
    private final JobDataService jobDataService;

    private final PatternLayoutEncoder patternLayoutEncoder;

    private final Map<String, ActiveSession> activeSessions = new ConcurrentHashMap<>();

    public BackendLibJobWebSocketHandler(
            final ObjectMapper objectMapper, final JobDataService jobDataService) {
        this.objectMapper = objectMapper;
        this.jobDataService = jobDataService;
        patternLayoutEncoder = new PatternLayoutEncoder();
        patternLayoutEncoder.setPattern(
                "%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr(%5p) %clr(---){faint}"
                        + " %clr([%15.15t]){magenta} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %m");
        patternLayoutEncoder.setCharset(StandardCharsets.UTF_8);
        patternLayoutEncoder.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        patternLayoutEncoder.start();
    }

    @Override
    public void handleTextMessage(final WebSocketSession session, final TextMessage message)
            throws IOException {
        final var jobRunId =
                objectMapper.readValue(message.getPayload(), LogRequestMessage.class).jobRunId;
        logger.trace("message with jobRunId: {}", jobRunId);
        activeSessions.putIfAbsent(
                session.getId(), new ActiveSession(session, jobRunId, new AtomicLong(0)));
    }

    @Override
    public void afterConnectionClosed(
            final WebSocketSession session, final @NotNull CloseStatus status) throws Exception {
        activeSessions.remove(session.getId());
        super.afterConnectionClosed(session, status);
    }

    public void sendLog() {
        logger.trace("send log to all active sessions");
        activeSessions.forEach(this::sendLogToSession);
    }

    private void sendLogToSession(final String sessionId, final ActiveSession activeSession) {
        final var session = activeSession.session();
        final var jobRunId = activeSession.jobRunId();
        final var counter = activeSession.counter();
        final var accumulatedLogLines = getAccumulatedLogLines(jobRunId, counter.get());
        if (accumulatedLogLines.lines() == 0) {
            return;
        }

        counter.addAndGet(accumulatedLogLines.lines());
        final var message = new TextMessage(accumulatedLogLines.log());
        logger.trace(
                "sending {} log lines with length {} to session {}",
                accumulatedLogLines.lines(),
                message.getPayloadLength(),
                sessionId);
        try {
            session.sendMessage(message);
        } catch (final IOException e) {
            throw new WebSocketException(e);
        }
    }

    private LogLineAccumulator.AccumulatedLogLines getAccumulatedLogLines(
            final Integer jobRunId, final long skipLines) {
        final var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger("ROOT").getAppender("console");

        return jobDataService
                .findLogsForJobRun(jobRunId, skipLines, BackendLibJobWebSocketHandler.PAGE_SIZE)
                .stream()
                .map(patternLayoutEncoder::encode)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .reduce(
                        new LogLineAccumulator(),
                        LogLineAccumulator::accumulate,
                        LogLineAccumulator::combine)
                .collectLog();
    }

    record LogRequestMessage(int jobRunId) {}

    record ActiveSession(WebSocketSession session, int jobRunId, AtomicLong counter) {}
}
