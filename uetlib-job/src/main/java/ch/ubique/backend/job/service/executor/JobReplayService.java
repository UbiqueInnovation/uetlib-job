package ch.ubique.backend.job.service.executor;

import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.service.JobService;

import org.springframework.stereotype.Service;

@Service
public class JobReplayService {
    private final JobService jobService;
    private final JobExecutorService jobExecutorService;

    public JobReplayService(
            final JobService jobService, final JobExecutorService jobExecutorService) {
        this.jobService = jobService;
        this.jobExecutorService = jobExecutorService;
    }

    public void submit(final JobRun jobRun) throws ClassNotFoundException {
        final var job = jobService.instantiateJob(jobService.getJobClass(jobRun.jobId()));
        final var jobRunArguments = jobRun.jobRunArguments();

        jobExecutorService.submit(job.getClass(), jobRunArguments);
    }
}
