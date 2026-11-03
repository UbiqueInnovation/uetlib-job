package ch.ubique.uetlib.job.data.repository;

import ch.ubique.uetlib.job.model.entity.K8sJobEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface K8sJobRepository extends JpaRepository<K8sJobEntity, Integer> {
    Optional<K8sJobEntity> findFirstByJobRunId(int jobRunId);
}
