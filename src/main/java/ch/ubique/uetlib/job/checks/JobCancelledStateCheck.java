package ch.ubique.uetlib.job.checks;

import ch.ubique.uetlib.job.checks.cache.JobStateCheckEvaluationCache;
import ch.ubique.uetlib.job.model.JobRunState;
import ch.ubique.uetlib.monitoring.check.models.CheckLevel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!job-run")
public class JobCancelledStateCheck extends JobStateCheck {

    public JobCancelledStateCheck(
            @Value("${ch.ubique.backend.job.check.cancelled-state:INFO}") final CheckLevel level,
            final JobStateCheckEvaluationCache jobStateCheckEvaluationCache) {
        super(level, JobRunState.CANCELLED, jobStateCheckEvaluationCache);
    }
}
