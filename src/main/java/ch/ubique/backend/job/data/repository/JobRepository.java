package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.JobEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
public interface JobRepository extends JpaRepository<JobEntity, Integer> {

    Optional<JobEntity> findById(int id);

    Optional<JobEntity> findByCanonicalClassName(String canonicalClassName);

    Stream<JobEntity> findAllByOrderById();

    Stream<JobEntity> findAllByIdInOrderById(Collection<Integer> id);

    Stream<JobEntity> findAllByIdNotInOrderById(Collection<Integer> id);
}
