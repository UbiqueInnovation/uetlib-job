package ch.ubique.uetlib.job.service.executor.runners;

import ch.ubique.uetlib.job.Job;
import ch.ubique.uetlib.job.model.JobRun;
import ch.ubique.uetlib.job.model.JobRunContext;
import ch.ubique.uetlib.job.model.JobRunState;
import ch.ubique.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.ubique.uetlib.job.service.JobService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.*;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("job-run")
public final class DedicatedJobRunner implements JobRunner {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private @Nullable JobRun activeJobRun;
    private @Nullable Future<?> future;
    private final ExecutorService executorService;

    private final JobService jobService;

    public DedicatedJobRunner(final JobService jobService) {
        this.jobService = jobService;
        final var threadFactory =
                new ThreadFactoryBuilder().setNameFormat("dedicated-job-runner").build();
        this.executorService = Executors.newSingleThreadExecutor(threadFactory);
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
        activeJobRun = jobRunContext.jobRun();
        future = executorService.submit(() -> JobRunnerHelper.run(job, jobService, jobRunContext));
        return true;
    }

    @Override
    public void cancel(final JobRun jobRun) {
        if (future != null) {
            future.cancel(true);
            activeJobRun = null;
        } else {
            logger.info("No job submitted so far. Cancel request is ignored.");
        }
    }

    @Override
    @Nullable
    public JobRunState resolveJobRunState(final JobRun jobRun) {
        // resolveJobRunState should not be called when the job-run profile is active.
        throw new IllegalStateException("Do not call resolveJobRunState for dedicated job runner.");
    }

    @Nullable
    public JobRun activeJobRun() {
        return activeJobRun;
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
