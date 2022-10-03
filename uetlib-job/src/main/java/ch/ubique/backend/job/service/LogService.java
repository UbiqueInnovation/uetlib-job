package ch.ubique.backend.job.service;

import ch.ubique.backend.job.data.service.JobDataService;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LogService {

    private static final Logger logger = LoggerFactory.getLogger(LogService.class);

    private final Map<String, Pair<Integer, AtomicLong>> subscribers = new ConcurrentHashMap<>();

    private final JobDataService jobDataService;

    public LogService(final JobDataService jobDataService) {
        this.jobDataService = jobDataService;
    }

    public void subscribe(final String sessionId, final int jobRunId) {
        subscribers.putIfAbsent(sessionId, Pair.of(jobRunId, new AtomicLong(0)));
    }

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS)
    public void pushLogs() {
        subscribers.forEach(
                (sessionId, pair) -> {
                    final int jobRunId = pair.getLeft();
                    final var counter = pair.getRight();
                    jobDataService
                            .findLogForJobRun(jobRunId)
                            .map(
                                    content -> {
                                        logger.debug(
                                                "new send for jobRunId {} with log in file {}",
                                                jobRunId,
                                                content);
                                        final String filePrefix = "file:";
                                        if (content.startsWith(filePrefix)) {
                                            final var file =
                                                    Path.of(content.substring(filePrefix.length()));
                                            try (final var lines = Files.lines(file)) {
                                                return lines;
                                            } catch (IOException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                        return content.lines();
                                    })
                            .ifPresent(
                                    lines ->
                                            lines.skip(counter.get())
                                                    .forEachOrdered(
                                                            line -> {
                                                                final var currentLine =
                                                                        counter.getAndIncrement();
                                                                logger.debug(
                                                                        "send line for jobRunId {}"
                                                                            + " for sessionId {}"
                                                                            + " (counter {}): {}",
                                                                        jobRunId,
                                                                        sessionId,
                                                                        currentLine,
                                                                        line);
                                                                send(sessionId, line, jobRunId);
                                                            }));
                });
    }

    private void send(final String sessionId, final String line, final int jobRunId) {
        // simpMessagingTemplate.convertAndSendToUser(sessionId, "/tail/" + jobRunId, line);
    }
}
