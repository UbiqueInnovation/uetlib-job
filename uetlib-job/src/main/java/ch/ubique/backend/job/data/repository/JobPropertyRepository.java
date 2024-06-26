package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.JobPropertyEntity;
import ch.ubique.backend.job.model.entity.JobPropertyId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.stream.Stream;

@Repository
public interface JobPropertyRepository extends JpaRepository<JobPropertyEntity, JobPropertyId> {

    Stream<JobPropertyEntity> findAllByJobId(int jobId);

    void deleteAllByJobId(int jobId);

    void deleteAllByKeyNotIn(Collection<String> jobPropertyName);
}
