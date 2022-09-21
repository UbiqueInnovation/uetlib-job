package ch.ubique.backend.job.data.jpa.repository;

import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.entity.JobEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<JobEntity, Integer> {

    Optional<Job> findByClazz(String clazz);

    List<Job> findAllByOrderByName();

    Optional<Job> findByName(String name);
}
