package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.JobTriggerEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface JobTriggerRepository extends JpaRepository<JobTriggerEntity, Long> {

    void deleteByJobId(final int jobId);

    Collection<JobTriggerEntity> findAllByJobId(final int jobId);
}
