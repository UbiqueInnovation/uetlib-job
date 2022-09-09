package ch.ubique.backend.job.data.jpa.repository;

import ch.ubique.backend.job.model.entity.JobRunInputEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRunInputRepository extends JpaRepository<JobRunInputEntity, Integer> {

    List<JobRunInputEntity> findAllByRunId(final Integer runId);
}
