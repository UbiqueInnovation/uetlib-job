package ch.uetlib.job.data.repository;

import ch.uetlib.job.model.entity.JobTriggerEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobTriggerRepository extends JpaRepository<JobTriggerEntity, Integer> {

    void deleteAllByJobId(int jobId);
}
