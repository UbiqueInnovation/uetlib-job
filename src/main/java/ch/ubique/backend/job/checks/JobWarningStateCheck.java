package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.monitoring.check.models.CheckLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!job-run")
public class JobWarningStateCheck extends JobStateCheck {

    public JobWarningStateCheck(
            @Value("${ch.ubique.backend.job.check.warning-state:WARNING}") final CheckLevel level,
            final JobDataService jobDataService, final JobService jobService) {
        super(level, JobRunState.WARNING, jobDataService, jobService);
    }
}
