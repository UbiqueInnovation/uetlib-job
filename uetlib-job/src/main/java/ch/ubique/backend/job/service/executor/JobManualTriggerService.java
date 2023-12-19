package ch.ubique.backend.job.service.executor;

import ch.ubique.backend.job.model.JobRun;
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
        final var job = jobService.instantiateJob(jobService.getJobClass(jobRun.jobId()));
        final var jobRunArguments = jobRun.jobRunArguments();

        jobExecutorService.submit(job.getClass(), jobRunArguments);
    }
}
