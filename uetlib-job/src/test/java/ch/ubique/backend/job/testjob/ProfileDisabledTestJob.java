package ch.ubique.backend.job.testjob;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobPropertiesConfig;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

@Profile("never-activated-profile-this-registered-job-should-not-be-instantiated")
@RegisteredJob
public class ProfileDisabledTestJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(ProfileDisabledTestJob.class);

    @Override
    public JobRunState run(final JobRunContext jobRunContext) {
        logger.debug("Running job with context {}", jobRunContext);
        return JobRunState.DONE;
    }

    @Override
    public @NotNull JobPropertiesConfig propertiesConfig() {
        return JobPropertiesConfig.initial(
                JobPropertyDefinitions.LAST_RUN_MAX_AGE.createProperty(Duration.ofDays(10000)));
    }
}
