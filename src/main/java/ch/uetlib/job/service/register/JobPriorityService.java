package ch.uetlib.job.service.register;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.service.JobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * A job's {@link ch.uetlib.job.Job#priority()} is defined in code, similar to fixed
 * triggers/properties. Keep the persisted value in sync so it can be used for DB-side ordering
 * (e.g. dequeueing).
 */
@Service
@Profile("!job-run")
public class JobPriorityService {

    private static final Logger logger = LoggerFactory.getLogger(JobPriorityService.class);

    private final JobService jobService;
    private final JobDataService jobDataService;

    public JobPriorityService(final JobService jobService, final JobDataService jobDataService) {
        this.jobService = jobService;
        this.jobDataService = jobDataService;
    }

    @EventListener(ApplicationReadyEvent.class)
    private void onStartup() {
        logger.info("Syncing priorities of all jobs");
        final var existingPriorities = jobDataService.getPriorities();
        for (final var jobClass : jobService.getAllJobClasses()) {
            final var jobId = jobService.findJobIdOrThrow(jobClass);
            final var codePriority = jobService.instantiateJob(jobClass).priority();
            final var currentPriority = existingPriorities.get(jobId);
            if (currentPriority == null || currentPriority != codePriority) {
                jobDataService.updatePriority(jobId, codePriority);
            }
        }
    }
}
