package ch.uetlib.job.service.submission;

import ch.uetlib.job.data.repository.JobRepository;
import ch.uetlib.job.data.repository.JobRunDatabaseSubmissionRepository;
import ch.uetlib.job.exception.JobSubmissionException;
import ch.uetlib.job.model.JobRunArgumentsAdditionalData;
import ch.uetlib.job.model.JobRunDatabaseSubmissionArguments;
import ch.uetlib.job.model.entity.JobRunDatabaseSubmissionEntity;
import ch.uetlib.job.model.triggers.DatabaseSubmissionTrigger;
import ch.uetlib.job.service.JobService;

import jakarta.transaction.Transactional;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modules that are not the job module use this service to submit new jobs to be trigger via {@link
 * DatabaseSubmissionTrigger}.
 *
 * <p>Keep the bean dependencies as minimal as possible. <strong>DO NOT add the {@link JobService}
 * as a dependency, this would make it impossible to include it in a non-job module.</strong>
 */
@Service
public class JobSubmissionService {

    private final JobRepository jobRepository;
    private final JobRunDatabaseSubmissionRepository jobRunDatabaseSubmissionRepository;

    /**
     * Job ids by canonical class name, filled lazily. Jobs are registered by the job module on its
     * own startup, so they may not exist yet when this bean is created, and they can be added
     * later.
     */
    private final Map<String, Integer> jobIds = new ConcurrentHashMap<>();

    public JobSubmissionService(
            final JobRepository jobRepository,
            final JobRunDatabaseSubmissionRepository jobRunDatabaseSubmissionRepository) {
        this.jobRepository = jobRepository;
        this.jobRunDatabaseSubmissionRepository = jobRunDatabaseSubmissionRepository;
    }

    private Optional<Integer> findJobId(final String jobClassName) {
        final var cached = jobIds.get(jobClassName);
        if (cached != null) {
            return Optional.of(cached);
        }

        final var loaded =
                jobRepository
                        .findByCanonicalClassName(jobClassName)
                        .map(job -> job.toRecord().id());
        loaded.ifPresent(id -> jobIds.put(jobClassName, id));
        return loaded;
    }

    @Transactional
    public void submitNewJobRunViaDatabaseTrigger(
            final String jobClassName,
            final boolean forceRun,
            @Nullable final JobRunArgumentsAdditionalData additionalData)
            throws JobSubmissionException {
        final var jobId =
                findJobId(jobClassName)
                        .orElseThrow(() ->
                                JobSubmissionException.withMessage(
                                        "No Job with clazz {0} found", jobClassName));
        jobRunDatabaseSubmissionRepository.save(
                new JobRunDatabaseSubmissionEntity(
                        jobId,
                        Instant.now(),
                        new JobRunDatabaseSubmissionArguments(forceRun, additionalData)));
    }
}
