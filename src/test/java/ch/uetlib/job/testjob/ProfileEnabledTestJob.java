package ch.uetlib.job.testjob;

import ch.uetlib.job.Job;
import ch.uetlib.job.RegisteredJob;
import ch.uetlib.job.model.JobRunContext;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.properties.JobPropertiesConfig;
import ch.uetlib.job.model.properties.JobPropertyDefinitions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

@Profile("test")
@RegisteredJob
public class ProfileEnabledTestJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(ProfileEnabledTestJob.class);

    @Override
    public JobRunState run(final JobRunContext jobRunContext) {
        logger.debug("Running job with context {}", jobRunContext);
        return JobRunState.DONE;
    }

    @Override
    public JobPropertiesConfig propertiesConfig() {
        return JobPropertiesConfig.initial(
                JobPropertyDefinitions.LAST_RUN_MAX_AGE.createProperty(Duration.ofDays(10000)));
    }
}
