package ch.ubique.backend.job.data.service;

import ch.ubique.backend.job.data.repository.JobRepository;
import ch.ubique.backend.job.data.repository.JobRunInputRepository;
import ch.ubique.backend.job.data.repository.JobRunRepository;
import ch.ubique.backend.job.model.JobRunInput;
import ch.ubique.backend.job.model.entity.JobRunInputEntity;

import java.util.Collection;

public class InputJobDataService extends JobDataService {

    private final JobRunInputRepository jobRunInputRepository;

    public InputJobDataService(
            JobRepository jobRepository,
            JobRunRepository jobRunRepository,
            JobRunInputRepository jobRunInputRepository) {
        super(jobRepository, jobRunRepository);
        this.jobRunInputRepository = jobRunInputRepository;
    }

    public void insertJobRunInput(JobRunInput jobRunInput) {
        if (!(jobRunInput instanceof JobRunInputEntity jobRunInputEntity)) {
            throw new ClassCastException(
                    "jobRun cannot be instance of class to JobRunInputEntity, but is of class"
                            + jobRunInput.getClass());
        }
        jobRunInputRepository.save(jobRunInputEntity);
    }

    public Collection<JobRunInput> findJobRunInputs(int runId) {
        return jobRunInputRepository.findAllByRunId(runId);
    }
}
