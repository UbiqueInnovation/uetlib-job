package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.JobPropertyKey;
import ch.ubique.backend.job.model.entity.JobPropertyEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobPropertyRepository extends JpaRepository<JobPropertyEntity, JobPropertyKey> {

    List<JobPropertyEntity> findAllByJobId(final int jobId);

    Optional<JobPropertyEntity> findByJobIdAndKey(final int jobId, final JobPropertyKey key);

    void deleteAllByJobId(final int jobId);
}
