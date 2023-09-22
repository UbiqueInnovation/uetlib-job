package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.monitoring.check.models.CheckLevel;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!job-run")
public class JobWarningStateCheck extends JobStateCheck {

    public JobWarningStateCheck(final JobDataService jobDataService) {
        super(JobRunState.WARNING, jobDataService);
    }

    @Override
    public CheckLevel level() {
        return CheckLevel.WARNING;
    }
}
