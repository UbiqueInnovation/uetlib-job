package ch.uetlib.job.listener;

import ch.uetlib.job.service.instance.JobDaemonRegistrationService;
import ch.uetlib.job.service.triggers.TriggerCoordinatorService;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

@Service
public class ApplicationReadyListener {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationReadyListener.class);

    private static final Profiles IS_PRIMARY_PROFILES =
            Profiles.of("!job-run & !" + TriggerCoordinatorService.NO_PRIMARY_PROFILE);

    private final Environment environment;
    private final ObjectProvider<TriggerCoordinatorService> triggerCoordinatorProvider;
    private final ObjectProvider<JobDaemonRegistrationService> jobDaemonRegistrationProvider;

    private @Nullable TriggerCoordinatorService triggerCoordinatorService;
    private @Nullable JobDaemonRegistrationService jobDaemonRegistrationService;

    public ApplicationReadyListener(
            final Environment environment,
            final ObjectProvider<TriggerCoordinatorService> triggerCoordinatorProvider,
            final ObjectProvider<JobDaemonRegistrationService> jobDaemonRegistrationProvider) {
        this.environment = environment;
        this.triggerCoordinatorProvider = triggerCoordinatorProvider;
        this.jobDaemonRegistrationProvider = jobDaemonRegistrationProvider;
    }

    @PostConstruct
    void resolveServices() {
        if (!coordinating()) {
            return;
        }
        triggerCoordinatorService = triggerCoordinatorProvider.getIfAvailable();
        jobDaemonRegistrationService = jobDaemonRegistrationProvider.getIfAvailable();
        if (triggerCoordinatorService == null || jobDaemonRegistrationService == null) {
            throw new IllegalStateException(
                    "Instances without the job-run or "
                            + TriggerCoordinatorService.NO_PRIMARY_PROFILE
                            + " profile coordinate triggers, which needs both "
                            + TriggerCoordinatorService.class.getSimpleName()
                            + " (present: "
                            + (triggerCoordinatorService != null)
                            + ") and "
                            + JobDaemonRegistrationService.class.getSimpleName()
                            + " (present: "
                            + (jobDaemonRegistrationService != null)
                            + ").");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void onStartup() {
        final var coordinator = triggerCoordinatorService;
        final var registration = jobDaemonRegistrationService;
        if (coordinator == null || registration == null) {
            logger.info("Not coordinating triggers on this instance.");
            return;
        }
        coordinator.prepareTriggers();
        registration.checkIn();
        coordinator.scheduleTriggers();
    }

    private boolean coordinating() {
        return environment.acceptsProfiles(IS_PRIMARY_PROFILES);
    }
}
