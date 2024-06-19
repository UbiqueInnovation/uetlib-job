package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.K8sJobEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface K8sJobRepository extends JpaRepository<K8sJobEntity, Integer> {}
