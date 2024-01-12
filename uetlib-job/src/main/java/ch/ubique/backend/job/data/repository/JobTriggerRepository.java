package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.JobTriggerEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface JobTriggerRepository extends JpaRepository<JobTriggerEntity, Integer> {

    void deleteAllByJobId(int jobId);

    Collection<JobTriggerEntity> findAllByJobId(int jobId);
}
