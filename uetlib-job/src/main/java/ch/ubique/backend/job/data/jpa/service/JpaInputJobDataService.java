package ch.ubique.backend.job.data.jpa.service;

import ch.ubique.backend.job.data.jpa.repository.JobRepository;
import ch.ubique.backend.job.data.jpa.repository.JobRunInputRepository;
import ch.ubique.backend.job.data.jpa.repository.JobRunRepository;
import ch.ubique.backend.job.data.service.InputJobDataService;
import ch.ubique.backend.job.model.JobRunInput;
import ch.ubique.backend.job.model.entity.JobRunInputEntity;

import java.util.Collection;

public class JpaInputJobDataService extends JpaJobDataService implements InputJobDataService {

    private final JobRunInputRepository jobRunInputRepository;

    public JpaInputJobDataService(
            JobRepository jobRepository,
            JobRunRepository jobRunRepository,
            JobRunInputRepository jobRunInputRepository) {
        super(jobRepository, jobRunRepository);
        this.jobRunInputRepository = jobRunInputRepository;
    }

    @Override
    public void insertJobRunInput(JobRunInput jobRunInput) {
        if (!(jobRunInput instanceof JobRunInputEntity jobRunInputEntity)) {
            throw new ClassCastException(
                    "jobRun cannot be instance of class to JobRunInputEntity, but is of class"
                            + jobRunInput.getClass());
        }
        jobRunInputRepository.save(jobRunInputEntity);
    }

    @Override
    public Collection<JobRunInput> findJobRunInputs(Integer runId) {
        return jobRunInputRepository.findAllByRunId(runId);
    }
}
