package ch.ubique.backend.job.service.executor;

import static java.util.Objects.requireNonNullElseGet;

import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.ReplayContext;
import ch.ubique.backend.job.service.JobService;

import org.springframework.stereotype.Service;

@Service
public class JobManualTriggerService {
    private final JobService jobService;
    private final JobExecutorService jobExecutorService;

    public JobManualTriggerService(
            final JobService jobService, final JobExecutorService jobExecutorService) {
        this.jobService = jobService;
        this.jobExecutorService = jobExecutorService;
    }

    public void replayJobRun(final int jobRunId) throws ClassNotFoundException {
        final var jobRun = jobService.getJobRun(jobRunId);
        final var jobClassAndId = jobService.findJobById(jobRun.jobId()).orElseThrow();
        final var jobClass = jobService.getJobClassOrThrow(jobClassAndId);
        final var previousArguments =
                requireNonNullElseGet(jobRun.jobRunArguments(), JobRunArguments::new);
        final var jobRunArguments =
                previousArguments.withReplayContext(new ReplayContext(jobRunId));

        jobExecutorService.submit(jobClass, jobRunArguments);
    }
}
