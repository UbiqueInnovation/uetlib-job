package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.JobEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<JobEntity, Integer> {

    Optional<JobEntity> findById(int id);

    Optional<JobEntity> findByCanonicalClassName(String canonicalClassName);

    List<JobEntity> findAllByOrderById();

    Page<JobEntity> findAllByOrderById(Pageable pageable);
}
