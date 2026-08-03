package ch.ubique.backend.job.service.executor;

import static java.util.Objects.requireNonNullElseGet;

import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.ReplayContext;
import ch.ubique.backend.job.service.JobService;

import org.springframework.stereotype.Service;

@Service
public class JobManualTriggerService {
    private final JobService jobService;

    public JobManualTriggerService(final JobService jobService) {
        this.jobService = jobService;
    }

    public void replayJobRun(final int jobRunId) {
        final var jobRun = jobService.getJobRun(jobRunId);
        final var jobClassAndId = jobService.findJobById(jobRun.jobId()).orElseThrow();
        final var previousArguments =
                requireNonNullElseGet(jobRun.jobRunArguments(), JobRunArguments::new);
        final var jobRunArguments =
                previousArguments.withReplayContext(new ReplayContext(jobRunId));
        jobService.queueNewRun(jobClassAndId, jobRunArguments, null);
    }
}
