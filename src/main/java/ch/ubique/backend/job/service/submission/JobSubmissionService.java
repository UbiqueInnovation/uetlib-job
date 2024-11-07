package ch.ubique.backend.job.service.submission;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.exception.JobSubmissionException;
import ch.ubique.backend.job.model.JobRunDatabaseSubmissionArguments;
import ch.ubique.backend.job.model.triggers.DatabaseSubmissionTrigger;
import ch.ubique.backend.job.service.JobService;

import jakarta.annotation.Nullable;

import org.springframework.stereotype.Service;

/**
 * Modules that are not the job module use this service to submit new jobs to be trigger via {@link
 * DatabaseSubmissionTrigger}.
 *
 * <p><strong>DO NOT add the {@link JobService} as a dependency, this would make it impossible to
 * include it in a non-job module.</strong>
 */
@Service
public class JobSubmissionService {

    private final JobDataService jobDataService;

    public JobSubmissionService(final JobDataService jobDataService) {
        this.jobDataService = jobDataService;
    }

    public void submitNewJobRunViaDatabaseTrigger(
            final String jobClassName,
            final boolean forceRun,
            @Nullable final Object additionalData)
            throws JobSubmissionException {
        final var jobId =
                jobDataService
                        .findJobByCanonicalClassName(jobClassName)
                        .orElseThrow(
                                JobSubmissionException.supplierWithMessage(
                                        "No Job with clazz {0} found", jobClassName))
                        .id();
        jobDataService.insertNewDatabaseSubmission(
                jobId, new JobRunDatabaseSubmissionArguments(forceRun, additionalData));
    }
}
