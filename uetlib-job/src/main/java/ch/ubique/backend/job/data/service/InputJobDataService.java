package ch.ubique.backend.job.data.service;

import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunInput;

import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

public interface InputJobDataService<J extends Job, R extends JobRun, I extends JobRunInput>
        extends JobDataService<J, R> {

    @Transactional
    void insertJobRunInput(I jobRunInput);

    @Transactional(readOnly = true)
    Collection<I> findJobRunInputs(Integer runId);
}
