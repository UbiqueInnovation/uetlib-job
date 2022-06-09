package ch.ubique.backend.job.data.jpa.repository;

import ch.ubique.backend.job.model.entity.JobStatusEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobStatusRepository extends JpaRepository<JobStatusEntity, Integer> {

    Optional<JobStatusEntity> findByClazz(String clazz);

    List<JobStatusEntity> findAllByOrderByName();

    Optional<JobStatusEntity> findByName(String name);
}
