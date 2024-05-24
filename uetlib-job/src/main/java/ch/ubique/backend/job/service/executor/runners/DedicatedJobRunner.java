package ch.ubique.backend.job.service.executor.runners;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.JobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Service
@Profile("job-run")
public final class DedicatedJobRunner implements JobRunner {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Future<?> future;
    private final ExecutorService executorService;

    private final JobService jobService;

    public DedicatedJobRunner(final JobService jobService) {
        this.jobService = jobService;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public JobRunnerKey key() {
        return JobRunnerKey.DEDICATED_K8S_JOB;
    }

    @Override
    public synchronized boolean submit(final Job job, final JobRunContext jobRunContext) {
        if (future != null) {
            logger.warn("Only one job is allowed to be submitted to the dedicated job runner.");
            return false;
        }
        future = executorService.submit(() -> JobRunnerHelper.run(job, jobService, jobRunContext));
        return true;
    }

    @Override
    public void cancel(final JobRun jobRun) {
        if (future != null) {
            future.cancel(true);
        }
    }

    public void awaitCompletion() {
        try {
            if (future != null) {
                future.get();
            }
            logger.info("Awaited completion.");
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Thread interrupted while waiting on job run.");
        } catch (final CancellationException e) {
            logger.info("Job run was cancelled.");
        } catch (final ExecutionException e) {
            logger.info("Exception while executing job run.", e);
        }
        logger.info("Going to shut down executor now.");
        executorService.shutdown();
    }
}
