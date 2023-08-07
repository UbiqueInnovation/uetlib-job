package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.JobTriggerEntity;
import ch.ubique.backend.job.model.triggers.TriggerType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface JobTriggerRepository extends JpaRepository<JobTriggerEntity, Long> {

    Collection<JobTriggerEntity> findAllByJobId(final int jobId);

    Collection<JobTriggerEntity> findByJobIdAndType(final int jobId, final TriggerType type);
}
