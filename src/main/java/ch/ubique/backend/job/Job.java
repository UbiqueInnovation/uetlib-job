package ch.ubique.backend.job;

import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobPropertiesConfig;
import ch.ubique.backend.job.model.triggers.JobTriggersConfig;

import org.jspecify.annotations.NonNull;

public interface Job {

    @SuppressWarnings("java:S112")
    JobRunState run(final JobRunContext jobRunContext) throws Exception;

    /** You can override this method to set initial or fixed properties for this job. */

    default JobPropertiesConfig propertiesConfig() {
        return JobPropertiesConfig.unset();
    }

    /** You can override this method to set initial or fixed triggers for this job. */

    default JobTriggersConfig triggersConfig() {
        return JobTriggersConfig.unset();
    }

    /** You can override this method to give the job a higher priority when waiting in a queue. */
    default int priority() {
        return 0;
    }

    default boolean allowParallelRuns() {
        return false;
    }

    /**
     * If true, the job is allowed to start a parallel run when force run is set, regardless of
     * {@link Job#allowParallelRuns()}.
     */
    default boolean forceRunOverridesAllowParallelRuns() {
        return true;
    }

    default boolean supportsForceRun() {
        return !allowParallelRuns() && forceRunOverridesAllowParallelRuns();
    }
}
