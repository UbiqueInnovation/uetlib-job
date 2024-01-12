package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.JobPropertyEntity;
import ch.ubique.backend.job.model.entity.JobPropertyId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobPropertyRepository extends JpaRepository<JobPropertyEntity, JobPropertyId> {

    List<JobPropertyEntity> findAllByJobId(int jobId);

    Optional<JobPropertyEntity> findByJobIdAndKey(int jobId, String key);

    void deleteAllByJobId(int jobId);

    void deleteAllByKeyNotIn(Collection<String> jobPropertyName);
}
