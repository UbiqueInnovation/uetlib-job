package ch.ubique.uetlib.job.service.triggers;

import ch.ubique.uetlib.job.model.JobClassAndId;
import ch.ubique.uetlib.job.model.JobRunArguments;
import ch.ubique.uetlib.job.model.triggers.RunOnStartupTrigger;
import ch.ubique.uetlib.job.model.triggers.TriggerType;
import java.util.function.Consumer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!job-run")
public class StartupTriggerService implements TriggerService<RunOnStartupTrigger> {

    @Override
    public void onStartup(
            final int triggerId,
            final RunOnStartupTrigger triggerDefinition,
            final JobClassAndId job,
            final Consumer<JobRunArguments> consumer) {
        // Just run it
        consumer.accept(new JobRunArguments(false, TriggerType.STARTUP, null, null, null));
    }

    @Override
    public void addTrigger(
            final int triggerId,
            final RunOnStartupTrigger triggerDefinition,
            final JobClassAndId job,
            final Consumer<JobRunArguments> consumer) {
        // Nothing to do here
    }

    @Override
    public void removeTrigger(final int triggerId) {
        // Nothing to do here
    }
}
