package ch.ubique.backend.job.config;

import ch.ubique.backend.job.exception.WebSocketException;
import ch.ubique.backend.job.service.logs.LogService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Profile("!job-run")
public class UetlibJobWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(UetlibJobWebSocketHandler.class);

    private final JsonMapper jsonMapper;
    private final LogService logService;

    private final Map<String, ActiveSession> activeSessions = new ConcurrentHashMap<>();

    public UetlibJobWebSocketHandler(final JsonMapper jsonMapper, final LogService logsService) {
        this.jsonMapper = jsonMapper;
        this.logService = logsService;
    }

    @Override
    public void handleTextMessage(final WebSocketSession session, final TextMessage message)
            throws IOException {
        final var jobRunId =
                jsonMapper.readValue(message.getPayload(), LogRequestMessage.class).jobRunId;
        logger.trace("message with jobRunId: {}", jobRunId);
        activeSessions.putIfAbsent(
                session.getId(), new ActiveSession(session, jobRunId, new AtomicLong(0)));
    }

    @Override
    public void afterConnectionClosed(final WebSocketSession session, final CloseStatus status)
            throws Exception {
        activeSessions.remove(session.getId());
        super.afterConnectionClosed(session, status);
    }

    public void sendLog() {
        logger.trace("send log to all active sessions");
        activeSessions.forEach(this::sendLogToSession);
    }

    private void sendLogToSession(final String sessionId, final ActiveSession activeSession) {
        try (final var session = activeSession.session()) {
            final var jobRunId = activeSession.jobRunId();
            final var counter = activeSession.counter();
            final var accumulatedLogLines = logService.logs(jobRunId, counter.get());
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
            session.sendMessage(message);
        } catch (final IOException e) {
            throw new WebSocketException(e);
        }
    }

    record LogRequestMessage(int jobRunId) {}

    record ActiveSession(WebSocketSession session, int jobRunId, AtomicLong counter) {}
}
