package ch.ubique.backend.job.runners;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public final class CurrentJobRunner implements JobRunner {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public JobRunnerKey key() {
        return JobRunnerKey.CURRENT;
    }

    @Override
    public JobRunState run(final Job job, final JobRunContext context) {
        MDC.put("jobRunId", String.valueOf(context.jobRun().id()));
        try {
            return job.run(context);
        } catch (final Exception e) {
            logger.error("Exception running job: ", e);
            return JobRunState.ERROR;
        } finally {
            MDC.remove("jobRunId");
        }
    }
}
