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

    List<JobPropertyEntity> findAllByJobId(final int jobId);

    Optional<JobPropertyEntity> findByJobIdAndIdKey(final int jobId, final String key);

    void deleteAllByIdKeyNotIn(final Collection<String> jobPropertyName);
}
