package ch.uetlib.job.checks;

import ch.uetlib.job.checks.cache.JobStateCheckEvaluationCache;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.monitoring.check.models.CheckLevel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!job-run")
public class JobUnfinishedStateCheck extends JobStateCheck {

    public JobUnfinishedStateCheck(
            @Value("${ch.ubique.backend.job.check.unfinished-state:WARNING}")
                    final CheckLevel level,
            final JobStateCheckEvaluationCache jobStateCheckEvaluationCache) {
        super(level, JobRunState.UNFINISHED, jobStateCheckEvaluationCache);
    }
}
