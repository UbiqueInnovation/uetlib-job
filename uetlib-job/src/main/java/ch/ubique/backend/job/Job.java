package ch.ubique.backend.job;

import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobPropertiesConfig;
import ch.ubique.backend.job.model.triggers.JobTriggersConfig;

import jakarta.validation.constraints.NotNull;

public interface Job {

    JobRunState run(final JobRunContext context) throws Exception;

    /**
     * The version is saved to the database for each job run. With this, we can track, what version
     * of the job was used for a given run.
     *
     * <p>The version is used by the {@link BaseInputJob} to determine, whether the job itself has
     * changed.
     */
    default int version() {
        return 0;
    }

    /** You can override this method to set initial or fixed properties for this job. */
    @NotNull
    default JobPropertiesConfig propertiesConfig() {
        return JobPropertiesConfig.unset();
    }

    /** You can override this method to set initial or fixed triggers for this job. */
    @NotNull
    default JobTriggersConfig triggersConfig() {
        return JobTriggersConfig.unset();
    }

    /** You can override this method to give the job a higher priority when waiting in a queue. */
    default int priority() {
        return 0;
    }
}
