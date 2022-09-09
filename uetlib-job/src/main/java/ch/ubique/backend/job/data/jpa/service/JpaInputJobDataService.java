package ch.ubique.backend.job.data.jpa.service;

import ch.ubique.backend.job.data.jpa.repository.JobRepository;
import ch.ubique.backend.job.data.jpa.repository.JobRunInputRepository;
import ch.ubique.backend.job.data.jpa.repository.JobRunRepository;
import ch.ubique.backend.job.data.service.InputJobDataService;
import ch.ubique.backend.job.model.entity.JobEntity;
import ch.ubique.backend.job.model.entity.JobRunEntity;
import ch.ubique.backend.job.model.entity.JobRunInputEntity;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class JpaInputJobDataService extends JpaJobDataService implements InputJobDataService<JobEntity, JobRunEntity, JobRunInputEntity> {

    private final JobRunInputRepository jobRunInputRepository;

    public JpaInputJobDataService(JobRepository jobRepository, JobRunRepository jobRunRepository, JobRunInputRepository jobRunInputRepository) {
        super(jobRepository, jobRunRepository);
        this.jobRunInputRepository = jobRunInputRepository;
    }

    @Override
    public void insertJobRunInput(JobRunInputEntity jobRunInput) {
        jobRunInputRepository.save(jobRunInput);
    }

    @Override
    public Collection<JobRunInputEntity> findJobRunInputs(Integer runId) {
        return jobRunInputRepository.findAllByRunId(runId);
    }
}
