package ch.ubique.backend.job.config;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.exception.WebSocketException;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.tuple.Triple;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Component
public class JobWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(JobWebSocketHandler.class);
    private static final int PAGE_SIZE = 1000;

    private final ObjectMapper objectMapper;
    private final JobDataService jobDataService;

    private final Map<String, Triple<WebSocketSession, Integer, AtomicLong>> activeSessions =
            new ConcurrentHashMap<>();

    public JobWebSocketHandler(
            final ObjectMapper objectMapper, final JobDataService jobDataService) {
        this.objectMapper = objectMapper;
        this.jobDataService = jobDataService;
    }

    @Override
    public void handleTextMessage(final WebSocketSession session, final TextMessage message)
            throws IOException {
        final var jobRunId = objectMapper.readValue(message.getPayload(), Message.class).jobRunId;
        logger.info("message with jobRunId: {}", jobRunId);
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
        logger.debug("send log to all active sessions");
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
                                            logger.debug(
                                                    "sending log with length {} to session {}",
                                                    message.getPayloadLength(),
                                                    sessionId);
                                            session.sendMessage(message);
                                        } catch (IOException e) {
                                            throw new WebSocketException(e);
                                        }
                                    });
                });
    }

    @NotNull
    private Optional<Stream<String>> getLogLines(final Integer jobRunId) {
        return jobDataService
                .findLogForJobRun(jobRunId)
                .map(
                        content -> {
                            final String filePrefix = "file:";
                            if (content.startsWith(filePrefix)) {
                                final var file = Path.of(content.substring(filePrefix.length()));
                                try {
                                    return Files.lines(file);
                                } catch (IOException e) {
                                    return Stream.of();
                                }
                            }
                            return content.lines();
                        });
    }

    record Message(int jobRunId) {}
}
