package ch.ubique.backend.job.checks;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRunState;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!job-run")
public class JobErrorStateCheck extends JobStateCheck {

    public JobErrorStateCheck(final JobDataService jobDataService) {
        super(JobRunState.ERROR, jobDataService);
    }
}
