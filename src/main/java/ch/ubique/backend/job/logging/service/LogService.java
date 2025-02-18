package ch.ubique.backend.job.logging.service;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.logging.AccumulatedLogLines;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Service
public class LogService extends TextWebSocketHandler {

    private static final int PAGE_SIZE = 5000;

    private final LogFormatterService logFormatterService;
    private final JobDataService jobDataService;

    public LogService(
            final LogFormatterService logFormatterService, final JobDataService jobDataService) {
        this.logFormatterService = logFormatterService;
        this.jobDataService = jobDataService;
    }

    public AccumulatedLogLines logs(final int jobRunId) {
        final var events = jobDataService.findAllLogsForJobRun(jobRunId);
        return logFormatterService.accumulatedLogLines(events);
    }

    public AccumulatedLogLines logs(final int jobRunId, final long skipLines) {
        final var events = jobDataService.findLogsForJobRun(jobRunId, skipLines, PAGE_SIZE);
        return logFormatterService.accumulatedLogLines(events);
    }
}
