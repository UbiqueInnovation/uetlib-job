package ch.ubique.backend.job.service.triggers;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.triggers.Trigger;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.executor.JobExecutorService;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!job-run & job-disable-triggers")
public class DisabledTriggerCoordinatorService extends TriggerCoordinatorService {

    private static final Logger logger =
            LoggerFactory.getLogger(DisabledTriggerCoordinatorService.class);

    public DisabledTriggerCoordinatorService(
            final JobDataService jobDataService,
            final JobService jobService,
            final JobExecutorService jobExecutorService,
            final Collection<TriggerService<?>> triggerServices)
            throws ClassNotFoundException {
        super(jobDataService, jobService, jobExecutorService, triggerServices);
    }

    @Override
    protected void onStartup(final Trigger trigger, final Class<? extends Job> jobClass) {
        logger.info("Ignoring onStartup: {} for {}", trigger, jobClass);
    }

    @Override
    public void addTrigger(final Class<? extends Job> jobClass, final Trigger trigger) {
        logger.info("Ignoring addTrigger: {} for {}", trigger, jobClass);
    }
}
