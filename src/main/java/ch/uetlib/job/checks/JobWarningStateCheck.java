package ch.uetlib.job.checks;

import ch.uetlib.job.checks.cache.JobStateCheckEvaluationCache;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.monitoring.check.models.CheckLevel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!job-run")
public class JobWarningStateCheck extends JobStateCheck {

    public JobWarningStateCheck(
            @Value("${ch.ubique.backend.job.check.warning-state:WARNING}") final CheckLevel level,
            final JobStateCheckEvaluationCache jobStateCheckEvaluationCache) {
        super(level, JobRunState.WARNING, jobStateCheckEvaluationCache);
    }
}
