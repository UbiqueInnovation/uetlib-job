package ch.uetlib.job.service.submission;

import static java.util.stream.Collectors.toMap;

import ch.uetlib.job.data.repository.JobRepository;
import ch.uetlib.job.data.repository.JobRunDatabaseSubmissionRepository;
import ch.uetlib.job.exception.JobSubmissionException;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRunArgumentsAdditionalData;
import ch.uetlib.job.model.JobRunDatabaseSubmissionArguments;
import ch.uetlib.job.model.entity.JobEntity;
import ch.uetlib.job.model.entity.JobRunDatabaseSubmissionEntity;
import ch.uetlib.job.model.triggers.DatabaseSubmissionTrigger;
import ch.uetlib.job.service.JobService;

import jakarta.transaction.Transactional;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Modules that are not the job module use this service to submit new jobs to be trigger via {@link
 * DatabaseSubmissionTrigger}.
 *
 * <p>Keep the bean dependencies as minimal as possible. <strong>DO NOT add the {@link JobService}
 * as a dependency, this would make it impossible to include it in a non-job module.</strong>
 */
@Service
public class JobSubmissionService {

    private final Map<String, Integer> jobIds;
    private final JobRunDatabaseSubmissionRepository jobRunDatabaseSubmissionRepository;

    public JobSubmissionService(
            final JobRepository jobRepository,
            final JobRunDatabaseSubmissionRepository jobRunDatabaseSubmissionRepository) {
        this.jobRunDatabaseSubmissionRepository = jobRunDatabaseSubmissionRepository;
        this.jobIds =
                jobRepository.findAll().stream()
                        .map(JobEntity::toRecord)
                        .collect(toMap(JobClassAndId::canonicalClassName, JobClassAndId::id));
    }

    @Transactional
    public void submitNewJobRunViaDatabaseTrigger(
            final String jobClassName,
            final boolean forceRun,
            @Nullable final JobRunArgumentsAdditionalData additionalData)
            throws JobSubmissionException {
        final var jobId = jobIds.get(jobClassName);
        if (jobId == null) {
            throw JobSubmissionException.withMessage("No Job with clazz {0} found", jobClassName);
        }
        jobRunDatabaseSubmissionRepository.save(
                new JobRunDatabaseSubmissionEntity(
                        jobId,
                        Instant.now(),
                        new JobRunDatabaseSubmissionArguments(forceRun, additionalData)));
    }
}
