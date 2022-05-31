package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.JobEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobRepository extends JpaRepository<JobEntity, Integer> {

    Optional<JobEntity> findByClazz(String clazz);

    List<JobEntity> findAllByOrderByName();

    Optional<JobEntity> findByName(String name);
}
