package ch.uetlib.job.service.logs;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.logging.AccumulatedLogLines;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class LogService {

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
        final var events = jobDataService.findLogsForJobRun(jobRunId, skipLines);
        return logFormatterService.accumulatedLogLines(events);
    }

    public Optional<Instant> mostRecentLogTimestamp(final int jobRunId) {
        return jobDataService.findMostRecentLoggingEventTimestamp(jobRunId);
    }
}
