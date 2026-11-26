package ch.uetlib.job.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.service.instance.JobDaemonRegistrationService;
import ch.uetlib.job.service.triggers.TriggerCoordinatorService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

/**
 * The listener exists on every instance, so it checks the profiles and looks the services up only
 * when the application is ready.
 */
class ApplicationReadyListenerTest {

    private final TriggerCoordinatorService coordinator = mock(TriggerCoordinatorService.class);
    private final JobDaemonRegistrationService registration =
            mock(JobDaemonRegistrationService.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<TriggerCoordinatorService> coordinatorProvider =
            mock(ObjectProvider.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<JobDaemonRegistrationService> registrationProvider =
            mock(ObjectProvider.class);

    @Test
    void aCoordinatingInstanceTakesTheLeaseBetweenPreparingAndSchedulingTheTriggers() {
        when(coordinatorProvider.getIfAvailable()).thenReturn(coordinator);
        when(registrationProvider.getIfAvailable()).thenReturn(registration);

        ready(listener());

        // The lease is taken between preparing and scheduling the triggers.
        final var order = inOrder(coordinator, registration);
        order.verify(coordinator).prepareTriggers();
        order.verify(registration).checkIn();
        order.verify(coordinator).scheduleTriggers();
    }

    @Test
    void aDedicatedJobRunPodLooksNothingUp() {
        // JobApplication starts --job pods with both profiles; neither service exists there.
        ready(listener("job-run", TriggerCoordinatorService.NO_PRIMARY_PROFILE));

        verifyNoInteractions(coordinatorProvider, registrationProvider);
    }

    @Test
    void anInstanceWithoutPrimaryLooksNothingUp() {
        ready(listener(TriggerCoordinatorService.NO_PRIMARY_PROFILE));

        verifyNoInteractions(coordinatorProvider, registrationProvider);
    }

    @Test
    void aCoordinatingInstanceWithoutTheDaemonRegistrationFailsBeforeItIsReady() {
        when(coordinatorProvider.getIfAvailable()).thenReturn(coordinator);
        when(registrationProvider.getIfAvailable()).thenReturn(null);

        final var listener = listener();
        final var failure = assertThrows(IllegalStateException.class, listener::resolveServices);

        assertTrue(failure.getMessage().contains("JobDaemonRegistrationService (present: false)"));
        verifyNoInteractions(coordinator);
    }

    @Test
    void aCoordinatingInstanceWithoutTheCoordinatorFailsBeforeItIsReady() {
        when(coordinatorProvider.getIfAvailable()).thenReturn(null);
        when(registrationProvider.getIfAvailable()).thenReturn(registration);

        final var listener = listener();
        final var failure = assertThrows(IllegalStateException.class, listener::resolveServices);

        assertTrue(failure.getMessage().contains("TriggerCoordinatorService (present: false)"));
    }

    /** What Spring does: {@code @PostConstruct} at creation, the listener once ready. */
    private static void ready(final ApplicationReadyListener listener) {
        listener.resolveServices();
        listener.onStartup();
    }

    private ApplicationReadyListener listener(final String... activeProfiles) {
        final var environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return new ApplicationReadyListener(environment, coordinatorProvider, registrationProvider);
    }
}
