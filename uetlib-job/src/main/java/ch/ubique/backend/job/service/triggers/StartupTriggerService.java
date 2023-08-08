package ch.ubique.backend.job.service.triggers;

import ch.ubique.backend.job.model.triggers.RunOnStartupTrigger;

import org.springframework.stereotype.Service;

@Service
public class StartupTriggerService implements TriggerService<RunOnStartupTrigger> {

    @Override
    public void onStartup(
            final int triggerId,
            final RunOnStartupTrigger triggerDefinition,
            final Runnable runnable) {
        // Just run it
        runnable.run();
    }

    @Override
    public void addTrigger(
            final int triggerId,
            final RunOnStartupTrigger triggerDefinition,
            final Runnable runnable) {
        throw new UnsupportedOperationException(
                "Can not add " + RunOnStartupTrigger.class + " after startup.");
    }

    @Override
    public void removeTrigger(final int triggerId) {
        // Nothing to do here
    }
}
