package ch.ubique.backend.job.model.executer;

import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;

public record SuccessfulSubmissionResult(JobRunnerKey runnerKey, JobRunContext jobRunContext)
        implements JobSubmissionResult {

    @Override
    public JobSubmissionStatus status() {
        return JobSubmissionStatus.SUBMITTED;
    }
}
