package ch.ubique.backend.job.data.service;

import ch.ubique.backend.job.model.JobRunInput;

import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

public interface InputJobDataService extends JobDataService {

    @Transactional
    void insertJobRunInput(JobRunInput jobRunInput);

    @Transactional(readOnly = true)
    Collection<JobRunInput> findJobRunInputs(int runId);
}
