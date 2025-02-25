package ch.ubique.backend.job.service.logs;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.logging.AccumulatedLogLines;

import org.springframework.stereotype.Service;

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
}
