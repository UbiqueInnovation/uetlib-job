package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.JobEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<JobEntity, Integer> {

    Optional<JobEntity> findById(int id);

    Optional<JobEntity> findByClazz(String clazz);

    List<JobEntity> findAllByOrderById();
}
