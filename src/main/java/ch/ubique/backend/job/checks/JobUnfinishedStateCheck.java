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
public class JobUnfinishedStateCheck extends JobStateCheck {

    public JobUnfinishedStateCheck(
            @Value("${ch.ubique.backend.job.check.unfinished-state:WARNING}")
                    final CheckLevel level,
            final JobDataService jobDataService, JobService jobService) {
        super(level, JobRunState.UNFINISHED, jobDataService, jobService);
    }
}
